import React, { useState, useRef, useEffect } from 'react';
import './ChatInput.css';

const ChatInput = ({ onSend, disabled }) => {
    const [input, setInput] = useState('');
    const textareaRef = useRef(null);

    // Auto-resize textarea
    useEffect(() => {
        const el = textareaRef.current;
        if (el) {
            el.style.height = 'auto';
            el.style.height = Math.min(el.scrollHeight, 160) + 'px';
        }
    }, [input]);

    const handleSubmit = (e) => {
        e.preventDefault();
        const trimmed = input.trim();
        if (!trimmed || disabled) return;
        onSend(trimmed);
        setInput('');
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit(e);
        }
    };

    return (
        <div className="chat-input-area">
            <form className="chat-input-form" onSubmit={handleSubmit}>
                <div className="input-wrapper glass">
                    <textarea
                        ref={textareaRef}
                        className="chat-textarea"
                        placeholder="Ask SFMC Copilot anything... (e.g., 'Create a data extension with fields Name, Email')"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        rows={1}
                        disabled={disabled}
                        id="chat-message-input"
                    />
                    <button
                        type="submit"
                        className={`send-btn ${input.trim() ? 'active' : ''}`}
                        disabled={!input.trim() || disabled}
                        id="send-message-btn"
                    >
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                            <path
                                d="M22 2L11 13"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            />
                            <path
                                d="M22 2L15 22L11 13L2 9L22 2Z"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            />
                        </svg>
                    </button>
                </div>
                <p className="input-hint">
                    SFMC Copilot uses <span className="hint-model">Gemini</span> &amp; <span className="hint-model">Ollama</span> via Spring AI.
                    Press <kbd>Enter</kbd> to send, <kbd>Shift+Enter</kbd> for new line.
                </p>
            </form>
        </div>
    );
};

export default ChatInput;
