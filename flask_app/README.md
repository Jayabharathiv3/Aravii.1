# AI-Powered Business Consultant (Flask Full-Stack App)

A complete, fully functional full-stack web application built with **Python (Flask)**, **SQLite**, **HTML5**, **CSS3**, and **Google Gemini 2.5 Flash API**.

---

## Folder Structure

```text
/flask_app
│   app.py                  # Main Flask application and all routes
│   requirements.txt        # Python library dependencies
│   README.md               # Setup & execution instructions
│   business_consultant.db  # SQLite database (auto-generated on first run)
│
├── /static
│   ├── /css
│   │       style.css       # Clean, modern responsive stylesheet
│   └── /uploads            # Temporary storage for uploaded audit images
│
└── /templates              # Jinja2 HTML templates
        base.html           # Common layout, navbar, flash alerts, footer
        login.html          # Authentication login screen
        register.html       # Authentication registration screen
        dashboard.html      # Executive dashboard with quick modules
        chat.html           # AI Consultant chat window with history
        image_analysis.html # Multimodal vision inspection & audit
        profile.html        # Business parameter management
```

---

## Prerequisites

- **Python 3.9+** installed on your system.
- A **Google Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).

---

## Step-by-Step Instructions to Run Locally in VS Code

### Step 1: Open the Project in VS Code
1. Launch **Visual Studio Code**.
2. Go to **File -> Open Folder...** and select the `flask_app` folder.
3. Open a new terminal in VS Code: **Terminal -> New Terminal** (or press `` Ctrl + ` `` / `` Cmd + ` ``).

### Step 2: Create and Activate a Virtual Environment

**On Windows (PowerShell / Command Prompt):**
```bash
python -m venv venv
.\venv\Scripts\activate
```

**On macOS / Linux:**
```bash
python3 -m venv venv
source venv/bin/activate
```

### Step 3: Install Required Libraries
```bash
pip install -r requirements.txt
```

### Step 4: Configure Your Gemini API Key
Open `app.py` in VS Code and locate line 17:
```python
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "YOUR_API_KEY_HERE")
```
Replace `"YOUR_API_KEY_HERE"` with your actual key:
```python
GEMINI_API_KEY = "AIzaSy..."
```
*(Alternatively, you can set the environment variable: `export GEMINI_API_KEY="AIzaSy..."` or `set GEMINI_API_KEY="AIzaSy..."`)*.

### Step 5: Start the Flask Application
```bash
python app.py
```

You will see output similar to:
```text
 * Serving Flask app 'app'
 * Debug mode: on
 * Running on http://127.0.0.1:5000
```

### Step 6: Open in Browser
Open your browser and navigate to:
```text
http://127.0.0.1:5000
```
1. Click **Sign Up** to create an account.
2. Log in and test:
   - **Ask AI Consultant**: Inquire about sales growth, pricing, or marketing.
   - **Image Analysis**: Upload a product, storefront, or chart image to get an AI audit.
   - **Business Profile**: Update your company details, budget, and goals.
