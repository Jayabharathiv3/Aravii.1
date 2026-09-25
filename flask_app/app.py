import os
import secrets
from datetime import datetime
from functools import wraps
from flask import (
    Flask, render_template, request, redirect, url_for,
    session, flash, jsonify
)
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename
from flask_sqlalchemy import SQLAlchemy
from PIL import Image
import google.generativeai as genai

# ==============================================================================
# CONFIGURATION SECTION
# Replace "YOUR_API_KEY_HERE" with your actual Google Gemini API Key,
# or set it in your environment as GEMINI_API_KEY.
# ==============================================================================
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "YOUR_API_KEY_HERE")

# Initialize Flask application
app = Flask(__name__)
app.secret_key = os.environ.get("FLASK_SECRET_KEY", "ai_biz_secret_key_" + secrets.token_hex(16))

# Database configuration (SQLite)
BASE_DIR = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = f"sqlite:///{os.path.join(BASE_DIR, 'business_consultant.db')}"
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Upload folder configuration
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'static', 'uploads')
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg'}
MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 Megabytes

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = MAX_FILE_SIZE

# Initialize SQLAlchemy
db = SQLAlchemy(app)

# Configure Google Generative AI
if GEMINI_API_KEY and GEMINI_API_KEY != "YOUR_API_KEY_HERE":
    genai.configure(api_key=GEMINI_API_KEY)


# ==============================================================================
# DATABASE MODELS
# ==============================================================================
class User(db.Model):
    __tablename__ = 'users'
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(256), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

    # Relationships
    profile = db.relationship('BusinessProfile', backref='user', uselist=False, cascade="all, delete-orphan")
    messages = db.relationship('ChatMessage', backref='user', lazy=True, cascade="all, delete-orphan")


class BusinessProfile(db.Model):
    __tablename__ = 'business_profiles'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'), unique=True, nullable=False)
    business_name = db.Column(db.String(150), default="")
    industry = db.Column(db.String(100), default="")
    budget = db.Column(db.String(100), default="")
    goals = db.Column(db.Text, default="")
    target_customers = db.Column(db.Text, default="")
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class ChatMessage(db.Model):
    __tablename__ = 'chat_messages'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'), nullable=False)
    sender = db.Column(db.String(10), nullable=False)  # 'user' or 'ai'
    content = db.Column(db.Text, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)


# Automatically create SQLite database tables
with app.app_context():
    db.create_all()


# ==============================================================================
# HELPER FUNCTIONS & DECORATORS
# ==============================================================================
def login_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if 'user_id' not in session:
            flash("Please log in to access this page.", "warning")
            return redirect(url_for('login'))
        return f(*args, **kwargs)
    return decorated_function


def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def get_current_user():
    if 'user_id' in session:
        return User.query.get(session['user_id'])
    return None


def get_gemini_model():
    """Returns a configured Gemini GenerativeModel or raises an exception if not ready."""
    if not GEMINI_API_KEY or GEMINI_API_KEY == "YOUR_API_KEY_HERE":
        raise ValueError(
            "Gemini API key is missing. Please set GEMINI_API_KEY at the top of app.py or in your environment."
        )
    return genai.GenerativeModel(
        model_name="gemini-2.5-flash",
        system_instruction=(
            "You are a senior executive AI Business Consultant. Provide clear, professional, "
            "actionable, and structured advice for questions and image audits. "
            "Use clear headings, numbered strategic points, financial considerations, and concrete next steps."
        )
    )


# ==============================================================================
# ROUTES
# ==============================================================================

@app.route('/')
def home():
    """Landing route: redirects to dashboard if authenticated, else to login."""
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    return redirect(url_for('login'))


@app.route('/register', methods=['GET', 'POST'])
def register():
    """User Registration: Validates inputs, hashes password, saves to SQLite."""
    if 'user_id' in session:
        return redirect(url_for('dashboard'))

    if request.method == 'POST':
        name = request.form.get('name', '').strip()
        email = request.form.get('email', '').strip().lower()
        password = request.form.get('password', '').strip()

        if not name or not email or not password:
            flash("Please fill in all fields (Name, Email, Password).", "danger")
            return render_template('register.html', name=name, email=email)

        if len(password) < 6:
            flash("Password must be at least 6 characters long.", "danger")
            return render_template('register.html', name=name, email=email)

        existing_user = User.query.filter_by(email=email).first()
        if existing_user:
            flash("An account with this email already exists. Please log in.", "danger")
            return redirect(url_for('login'))

        # Hash password securely
        hashed_password = generate_password_hash(password)
        new_user = User(name=name, email=email, password_hash=hashed_password)
        db.session.add(new_user)
        db.session.commit()

        # Create empty business profile linked to user
        default_profile = BusinessProfile(
            user_id=new_user.id,
            business_name=f"{name}'s Business",
            industry="General Retail & Services",
            budget="$10,000",
            goals="Expand customer acquisition and revenue",
            target_customers="Local consumers & digital shoppers"
        )
        db.session.add(default_profile)
        db.session.commit()

        # Log the user in via session
        session['user_id'] = new_user.id
        session['user_name'] = new_user.name
        flash("Registration successful! Welcome to AI Business Consultant.", "success")
        return redirect(url_for('dashboard'))

    return render_template('register.html')


@app.route('/login', methods=['GET', 'POST'])
def login():
    """User Login: Verifies credentials, initializes session."""
    if 'user_id' in session:
        return redirect(url_for('dashboard'))

    if request.method == 'POST':
        email = request.form.get('email', '').strip().lower()
        password = request.form.get('password', '').strip()

        if not email or not password:
            flash("Please provide both email and password.", "danger")
            return render_template('login.html', email=email)

        user = User.query.filter_by(email=email).first()
        if not user or not check_password_hash(user.password_hash, password):
            flash("Invalid email or password. Please try again.", "danger")
            return render_template('login.html', email=email)

        session['user_id'] = user.id
        session['user_name'] = user.name
        flash(f"Welcome back, {user.name}!", "success")
        return redirect(url_for('dashboard'))

    return render_template('login.html')


@app.route('/logout')
def logout():
    """Logs out the user and clears session data."""
    session.clear()
    flash("You have been successfully logged out.", "info")
    return redirect(url_for('login'))


@app.route('/dashboard')
@login_required
def dashboard():
    """Executive Dashboard with navigation cards and business profile overview."""
    user = get_current_user()
    profile = BusinessProfile.query.filter_by(user_id=user.id).first()
    message_count = ChatMessage.query.filter_by(user_id=user.id).count()

    return render_template(
        'dashboard.html',
        user=user,
        profile=profile,
        message_count=message_count,
        has_api_key=(GEMINI_API_KEY and GEMINI_API_KEY != "YOUR_API_KEY_HERE")
    )


@app.route('/chat', methods=['GET', 'POST'])
@login_required
def chat():
    """Ask AI Consultant: Handles conversational Q&A and stores history."""
    user = get_current_user()
    profile = BusinessProfile.query.filter_by(user_id=user.id).first()

    if request.method == 'POST':
        question = request.form.get('question', '').strip()

        if not question:
            flash("Please enter a question to ask the AI Consultant.", "warning")
            return redirect(url_for('chat'))

        # Save user question in database
        user_msg = ChatMessage(user_id=user.id, sender='user', content=question)
        db.session.add(user_msg)
        db.session.commit()

        # Build context from business profile
        context_prompt = ""
        if profile and profile.business_name:
            context_prompt = (
                f"[Client Business Profile: {profile.business_name}, Industry: {profile.industry}, "
                f"Budget: {profile.budget}, Goals: {profile.goals}, Target: {profile.target_customers}]\n\n"
            )

        full_prompt = f"{context_prompt}Client Question: {question}"

        # Call Gemini 2.5 Flash API
        try:
            model = get_gemini_model()
            response = model.generate_content(full_prompt)
            ai_reply = response.text if response and response.text else "No response generated."

            # Save AI reply in database
            ai_msg = ChatMessage(user_id=user.id, sender='ai', content=ai_reply)
            db.session.add(ai_msg)
            db.session.commit()

        except Exception as e:
            error_message = f"AI Consultant Error: {str(e)}"
            ai_msg = ChatMessage(
                user_id=user.id,
                sender='ai',
                content=f"⚠️ {error_message}\n\nPlease verify your GEMINI_API_KEY in app.py."
            )
            db.session.add(ai_msg)
            db.session.commit()
            flash(error_message, "danger")

        return redirect(url_for('chat'))

    # Load conversation history ordered chronologically
    messages = ChatMessage.query.filter_by(user_id=user.id).order_by(ChatMessage.created_at.asc()).all()
    return render_template(
        'chat.html',
        user=user,
        messages=messages,
        profile=profile,
        has_api_key=(GEMINI_API_KEY and GEMINI_API_KEY != "YOUR_API_KEY_HERE")
    )


@app.route('/chat/clear', methods=['POST'])
@login_required
def clear_chat():
    """Clears conversation history for the logged-in user."""
    user = get_current_user()
    ChatMessage.query.filter_by(user_id=user.id).delete()
    db.session.commit()
    flash("Chat history cleared successfully.", "info")
    return redirect(url_for('chat'))


@app.route('/image-analysis', methods=['GET', 'POST'])
@login_required
def image_analysis():
    """Image Analysis: Uploads photo, sends to Gemini 2.5 Flash multimodal, displays findings."""
    user = get_current_user()
    profile = BusinessProfile.query.filter_by(user_id=user.id).first()

    image_filename = None
    ai_analysis = None
    custom_prompt = None

    if request.method == 'POST':
        if 'image' not in request.files:
            flash("No file part provided in request.", "danger")
            return redirect(request.url)

        file = request.files['image']
        custom_prompt = request.form.get('prompt', '').strip()

        if file.filename == '':
            flash("No image file was selected. Please choose a file.", "warning")
            return redirect(request.url)

        if not allowed_file(file.filename):
            flash("Invalid file format. Only JPG, JPEG, and PNG images are allowed.", "danger")
            return redirect(request.url)

        try:
            # Secure filename and save to static/uploads
            filename = f"user_{user.id}_{int(datetime.utcnow().timestamp())}_{secure_filename(file.filename)}"
            filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            file.save(filepath)
            image_filename = filename

            # Open image with PIL for Gemini multimodal input
            pil_image = Image.open(filepath)

            # Build prompt
            prompt_text = (
                "You are an executive AI Business Consultant. "
                "Analyze this business image (storefront, product, invoice, chart, or retail display). "
                "1. Identify what is depicted.\n"
                "2. Provide key business observations and strategic insights.\n"
                "3. Recommend actionable growth, pricing, or operational improvements.\n"
            )
            if profile and profile.business_name:
                prompt_text += f"\nClient Business Context: {profile.business_name} ({profile.industry})."
            if custom_prompt:
                prompt_text += f"\nUser's specific inquiry: {custom_prompt}"

            # Call Gemini 2.5 Flash with multimodal image
            model = get_gemini_model()
            response = model.generate_content([prompt_text, pil_image])
            ai_analysis = response.text if response and response.text else "No analysis produced."

        except Exception as e:
            flash(f"Error during image analysis: {str(e)}", "danger")

    return render_template(
        'image_analysis.html',
        user=user,
        image_filename=image_filename,
        ai_analysis=ai_analysis,
        custom_prompt=custom_prompt,
        has_api_key=(GEMINI_API_KEY and GEMINI_API_KEY != "YOUR_API_KEY_HERE")
    )


@app.route('/profile', methods=['GET', 'POST'])
@login_required
def profile():
    """Business Profile: Displays and updates company parameters."""
    user = get_current_user()
    profile = BusinessProfile.query.filter_by(user_id=user.id).first()

    if request.method == 'POST':
        business_name = request.form.get('business_name', '').strip()
        industry = request.form.get('industry', '').strip()
        budget = request.form.get('budget', '').strip()
        goals = request.form.get('goals', '').strip()
        target_customers = request.form.get('target_customers', '').strip()

        if not business_name:
            flash("Business name is required.", "danger")
            return render_template('profile.html', user=user, profile=profile)

        if not profile:
            profile = BusinessProfile(user_id=user.id)
            db.session.add(profile)

        profile.business_name = business_name
        profile.industry = industry
        profile.budget = budget
        profile.goals = goals
        profile.target_customers = target_customers
        profile.updated_at = datetime.utcnow()

        db.session.commit()
        flash("Business Profile saved successfully! Future AI consultations will use these parameters.", "success")
        return redirect(url_for('profile'))

    return render_template('profile.html', user=user, profile=profile)


# Error Handlers
@app.errorhandler(413)
def request_entity_too_large(error):
    flash("Uploaded file exceeds the maximum allowed size (5 MB).", "danger")
    return redirect(url_for('image_analysis')), 413


@app.errorhandler(404)
def page_not_found(error):
    return render_template('base.html', not_found=True), 404


if __name__ == '__main__':
    # Run the Flask development server locally on port 5000
    app.run(debug=True, host='127.0.0.1', port=5000)
