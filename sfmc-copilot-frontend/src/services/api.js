const API_BASE_URL = 'http://localhost:8080/api';

// Set to false to use the live Spring Boot backend
// Set to true for frontend-only development with mock responses
const USE_MOCK = false;

// Simulated SFMC Copilot responses for frontend development
const mockResponses = [
    {
        text: "I'd be happy to help you create that Data Extension! Here's what I'll set up:\n\n**Data Extension:** `Holiday_Promo`\n\n| Field Name | Data Type | Required |\n|------------|-----------|----------|\n| SubscriberKey | Text (254) | ✅ Primary Key |\n| FirstName | Text (100) | ❌ |\n| Email | EmailAddress | ✅ |\n| PurchaseDate | Date | ❌ |\n\nShall I go ahead and create this Data Extension in your SFMC account?",
        model: 'gemini',
        action: {
            type: 'CREATE_DATA_EXTENSION',
            name: 'Holiday_Promo',
            status: 'pending_confirmation'
        }
    },
    {
        text: "Great question! Let me look into your current automations.\n\n📊 **Your SFMC Account Summary:**\n- **Data Extensions:** 24 active\n- **Automations:** 8 running, 3 paused\n- **Triggered Sends:** 12 active\n- **Total Subscribers:** 156,432\n\nWould you like me to drill down into any of these?",
        model: 'gemini',
        action: null
    },
    {
        text: "I'll create that email template for you. Here's the setup:\n\n✉️ **Email Template**\n- **Subject:** Holiday Sale — 30% Off!\n- **Preheader:** Don't miss our biggest sale of the year\n- **Template Type:** HTML + Content Blocks\n\nI've drafted a responsive HTML template with your brand colors. Want me to proceed with creating it in Content Builder?",
        model: 'ollama',
        action: {
            type: 'CREATE_EMAIL',
            name: 'Holiday Sale Email',
            status: 'pending_confirmation'
        }
    },
    {
        text: "Sure! I'll set up that automation for you:\n\n⚙️ **Automation Configuration:**\n- **Name:** Weekly_Holiday_Campaign\n- **Schedule:** Every Monday at 9:00 AM CST\n- **Steps:**\n  1. SQL Query → Filter active subscribers\n  2. Data Extract → Prepare send list\n  3. Send Email → Holiday_Sale_Template\n\nThis automation will run automatically each week. Should I create it now?",
        model: 'gemini',
        action: {
            type: 'CREATE_AUTOMATION',
            name: 'Weekly_Holiday_Campaign',
            status: 'pending_confirmation'
        }
    },
    {
        text: "Here's a summary based on your recent campaign data:\n\n📈 **Campaign Performance (Last 30 Days)**\n- **Emails Sent:** 45,621\n- **Open Rate:** 24.3% *(industry avg: 21.5%)*\n- **Click Rate:** 3.8% *(industry avg: 2.3%)*\n- **Unsubscribe Rate:** 0.12%\n- **Bounce Rate:** 1.4%\n\nYour campaigns are performing above industry average! 🎉 Want me to set up an A/B test to optimize further?",
        model: 'ollama',
        action: null
    }
];

let mockIndex = 0;

/**
 * Send a chat message to the backend (or mock service)
 * @param {string} message - The user's message
 * @param {string} conversationId - Conversation identifier
 * @param {string} preferredModel - LLM preference: "auto", "gemini", or "ollama"
 * @returns {Promise<{text: string, model: string, action: object|null}>}
 */
export async function sendMessage(message, conversationId = 'default', preferredModel = 'auto') {
    if (USE_MOCK) {
        return getMockResponse(message);
    }

    try {
        const response = await fetch(`${API_BASE_URL}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message,
                conversationId,
                preferredModel,
            }),
        });

        if (!response.ok) {
            const errorBody = await response.text();
            throw new Error(`API error ${response.status}: ${errorBody}`);
        }

        return await response.json();
    } catch (error) {
        console.error('API call failed:', error);
        throw error;
    }
}

/**
 * Returns a simulated response with realistic delay
 */
function getMockResponse(message) {
    return new Promise((resolve) => {
        const delay = 1000 + Math.random() * 2000; // 1-3s realistic delay
        setTimeout(() => {
            const response = mockResponses[mockIndex % mockResponses.length];
            mockIndex++;
            resolve({
                text: response.text,
                model: response.model,
                action: response.action,
            });
        }, delay);
    });
}

/**
 * Confirm an action (e.g., user approves creating a Data Extension)
 */
export async function confirmAction(actionId, confirmed) {
    if (USE_MOCK) {
        return new Promise((resolve) => {
            setTimeout(() => {
                resolve({
                    text: confirmed
                        ? "✅ Done! The operation has been executed successfully in your SFMC account."
                        : "❌ Operation cancelled. Let me know if you'd like to make any changes.",
                    model: 'gemini',
                    action: null,
                });
            }, 800);
        });
    }

    const response = await fetch(`${API_BASE_URL}/chat/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ actionId, confirmed }),
    });

    return await response.json();
}

/**
 * Check backend health status
 * @returns {Promise<{status: string, service: string}>}
 */
export async function checkHealth() {
    try {
        const response = await fetch(`${API_BASE_URL}/health`);
        if (!response.ok) throw new Error(`Health check failed: ${response.status}`);
        return await response.json();
    } catch (error) {
        console.error('Health check failed:', error);
        return { status: 'DOWN', service: 'SFMC Copilot Backend' };
    }
}
