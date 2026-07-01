// chrome-extension/content-scripts/whatsapp.js
// Content script for WhatsApp Web integration

console.log('WhatsApp Web integration loaded');

// Observer to detect new messages
let messageObserver = null;

// Initialize the WhatsApp integration
function initWhatsAppIntegration() {
    console.log('Initializing WhatsApp Web integration');

    // Wait for the chat interface to load
    waitForElement('.copyable-text.selectable-text', () => {
        console.log('WhatsApp Web interface detected, starting message monitoring');

        // Start observing messages
        startObservingMessages();

        // Inject the floating window UI
        injectFloatingWindow();
    });
}

// Wait for specific element to appear
function waitForElement(selector, callback) {
    const element = document.querySelector(selector);
    if (element) {
        callback(element);
        return;
    }

    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            mutation.addedNodes.forEach((node) => {
                if (node.nodeType === 1) { // Element node
                    const element = node.querySelector ? node.querySelector(selector) : null;
                    if (element) {
                        callback(element);
                        observer.disconnect();
                    }
                }
            });
        });
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });
}

// Start observing messages
function startObservingMessages() {
    // Watch for new message nodes being added
    messageObserver = new MutationObserver((mutationsList) => {
        for (let mutation of mutationsList) {
            if (mutation.type === 'childList') {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType === 1) { // Element node
                        // Check if this is a new message element
                        if (isMessageElement(node)) {
                            processMessageElement(node);
                        }

                        // Also check child nodes
                        const messageElements = node.querySelectorAll('[data-pre-plain-text]');
                        messageElements.forEach(msgEl => processMessageElement(msgEl));
                    }
                });
            }
        }
    });

    // Observe the main chat area
    const chatContainer = document.querySelector('#main');
    if (chatContainer) {
        messageObserver.observe(chatContainer, {
            childList: true,
            subtree: true
        });
        console.log('Started observing messages in WhatsApp Web');
    } else {
        console.log('Chat container not found, retrying...');
        setTimeout(startObservingMessages, 2000);
    }
}

// Check if element is a message element
function isMessageElement(element) {
    return element.classList.contains('_3Whw5') || // Incoming message
           element.classList.contains('_1ZMSM') || // Outgoing message
           element.querySelector('[data-pre-plain-text]'); // Any message with plain text
}

// Process a message element
function processMessageElement(element) {
    const messageText = getMessageText(element);

    if (messageText && !isOwnMessage(element)) {
        const senderName = getSenderName(element);

        console.log('Detected incoming message:', messageText);

        // Send message to background script for processing
        chrome.runtime.sendMessage({
            action: 'MESSAGE_RECEIVED',
            data: {
                content: messageText,
                senderName: senderName,
                platform: 'WHATSAPP_WEB',
                timestamp: new Date().toISOString()
            }
        }, (response) => {
            if (chrome.runtime.lastError) {
                console.error('Error sending message to background:', chrome.runtime.lastError);
            } else {
                console.log('Message sent to background for processing');
            }
        });
    }
}

// Extract message text from element
function getMessageText(element) {
    // Try various selectors for message content
    let messageElement = element.querySelector('span[dir="ltr"]') ||
                        element.querySelector('div[tabindex="-1"] span') ||
                        element.querySelector('[data-pre-plain-text]');

    if (!messageElement) {
        // Check if the element itself contains the message
        messageElement = element;
    }

    // Get text content, handling different possible structures
    const text = messageElement.textContent || messageElement.innerText || '';
    return text.trim().substring(0, 500); // Limit to 500 characters
}

// Check if message is from the current user
function isOwnMessage(element) {
    // Check for class that indicates outgoing message
    return element.classList.contains('_1ZMSM') ||
           element.classList.contains('message-out') ||
           (element.closest && element.closest('[data-pre-plain-text]') === null);
}

// Get sender name
function getSenderName(element) {
    // Try to find sender name in the message context
    const senderElement = element.closest('[data-pre-plain-text]') ||
                         element.closest('.copyable-text.selectable-text');

    if (senderElement && senderElement.dataset.prePlainText) {
        return senderElement.dataset.prePlainText.replace(/^"|"$/g, '');
    }

    // Fallback to current chat name
    const chatTitle = document.querySelector('#main header span');
    return chatTitle ? chatTitle.textContent : 'Unknown Contact';
}

// Inject floating window UI
function injectFloatingWindow() {
    // Check if already injected
    if (document.getElementById('csb-floating-window')) {
        return;
    }

    // Create floating window container
    const floatingWindow = document.createElement('div');
    floatingWindow.id = 'csb-floating-window';
    floatingWindow.style.cssText = `
        position: fixed;
        bottom: 20px;
        right: 20px;
        width: 320px;
        background: white;
        border-radius: 12px;
        box-shadow: 0 4px 20px rgba(0,0,0,0.15);
        z-index: 10000;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        display: none;
        flex-direction: column;
        max-height: 60vh;
        overflow: hidden;
    `;

    // Create header
    const header = document.createElement('div');
    header.className = 'csb-header';
    header.style.cssText = `
        padding: 12px 16px;
        background: #f8f9fa;
        border-bottom: 1px solid #e9ecef;
        display: flex;
        justify-content: space-between;
        align-items: center;
    `;
    header.innerHTML = `
        <h3 style="margin: 0; font-size: 14px; font-weight: 600;">AI Reply Suggestions</h3>
        <button id="csb-close-btn" style="
            background: none;
            border: none;
            font-size: 18px;
            cursor: pointer;
            color: #6c757d;
            padding: 0;
            width: 24px;
            height: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
        ">×</button>
    `;

    // Create content area
    const contentArea = document.createElement('div');
    contentArea.id = 'csb-suggestions-container';
    contentArea.style.cssText = `
        padding: 12px 16px;
        overflow-y: auto;
        flex-grow: 1;
    `;

    // Add elements to window
    floatingWindow.appendChild(header);
    floatingWindow.appendChild(contentArea);
    document.body.appendChild(floatingWindow);

    // Add event listeners
    document.getElementById('csb-close-btn').addEventListener('click', () => {
        floatingWindow.style.display = 'none';
    });

    // Listen for suggestions from background script
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (request.action === 'SUGGESTION_RECEIVED') {
            showSuggestions(request.data);
        }
    });
}

// Show suggestions in the floating window
function showSuggestions(suggestions) {
    const container = document.getElementById('csb-suggestions-container');
    const window = document.getElementById('csb-floating-window');

    if (!container || !window) return;

    // Clear previous suggestions
    container.innerHTML = '';

    // Handle both single suggestion and array of suggestions
    const suggestionList = Array.isArray(suggestions) ? suggestions : [suggestions];

    if (suggestionList.length > 0 && suggestionList[0].content) {
        suggestionList.forEach((suggestion, index) => {
            const suggestionDiv = document.createElement('div');
            suggestionDiv.className = 'csb-suggestion-item';
            suggestionDiv.style.cssText = `
                background: #f8f9fa;
                border-radius: 8px;
                padding: 10px 12px;
                margin-bottom: 8px;
                cursor: pointer;
                transition: background-color 0.2s;
            `;
            suggestionDiv.innerHTML = `
                <div style="font-size: 14px; margin-bottom: 4px;">${suggestion.content}</div>
                <div style="font-size: 11px; color: #6c757d;">
                    Confidence: ${(suggestion.confidence * 100).toFixed(0)}%
                </div>
            `;

            suggestionDiv.addEventListener('click', () => {
                insertSuggestionIntoChat(suggestion.content);
                window.style.display = 'none';
            });

            container.appendChild(suggestionDiv);
        });

        // Show the floating window
        window.style.display = 'flex';
    } else {
        container.innerHTML = '<div style="color: #6c757d; text-align: center; padding: 20px;">No suggestions available</div>';
        window.style.display = 'flex';
    }
}

// Insert suggestion into WhatsApp chat
function insertSuggestionIntoChat(text) {
    const inputElement = document.querySelector('div[contenteditable="true"][data-tab="1"]');

    if (inputElement) {
        // Clear the input
        inputElement.innerHTML = '';

        // Insert the text
        inputElement.focus();

        // Create a text node with the suggestion
        const textNode = document.createTextNode(text);

        // Add to the input element
        inputElement.appendChild(textNode);

        // Trigger input event to ensure WhatsApp detects the change
        const event = new Event('input', { bubbles: true });
        inputElement.dispatchEvent(event);

        console.log('Inserted suggestion into chat:', text);
    } else {
        console.error('Could not find WhatsApp input element');
    }
}

// Initialize when DOM is loaded
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWhatsAppIntegration);
} else {
    // DOM is already loaded, initialize immediately
    setTimeout(initWhatsAppIntegration, 2000); // Wait a bit for WhatsApp to fully load
}