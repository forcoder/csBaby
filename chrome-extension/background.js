// chrome-extension/background.js
// Background script for the Customer Service Automation Extension

console.log('Customer Service Automation Extension loaded');

// Store extension state
let extensionEnabled = true;
let currentTabId = null;

// Listen for messages from content scripts
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    console.log('Received message from content script:', request);

    switch(request.action) {
        case 'MESSAGE_RECEIVED':
            handleIncomingMessage(request.data);
            sendResponse({status: 'received'});
            break;

        case 'GET_EXTENSION_STATUS':
            sendResponse({enabled: extensionEnabled});
            break;

        case 'TOGGLE_EXTENSION':
            extensionEnabled = !extensionEnabled;
            sendResponse({enabled: extensionEnabled});
            break;

        default:
            console.warn('Unknown action:', request.action);
            sendResponse({error: 'Unknown action'});
    }

    // Return true to indicate that the response will be sent asynchronously
    return true;
});

// Handle incoming messages
async function handleIncomingMessage(messageData) {
    if (!extensionEnabled) {
        console.log('Extension is disabled, skipping message processing');
        return;
    }

    console.log('Processing incoming message:', messageData);

    try {
        // Send message to backend API for AI processing
        const response = await fetch('http://localhost:8080/api/v1/messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                conversationId: messageData.conversationId || 'web_conv_' + Date.now(),
                senderId: messageData.senderId || 'web_user',
                senderName: messageData.senderName || 'Web User',
                content: messageData.content,
                messageType: 'INCOMING',
                platform: messageData.platform || 'WEB',
                timestamp: new Date().toISOString()
            })
        });

        if (response.ok) {
            const suggestion = await response.json();
            console.log('Received suggestion from backend:', suggestion);

            // Send the suggestion back to the content script
            if (currentTabId) {
                chrome.tabs.sendMessage(currentTabId, {
                    action: 'SUGGESTION_RECEIVED',
                    data: suggestion
                });
            }
        } else {
            console.error('Failed to get suggestion from backend:', response.statusText);
        }
    } catch (error) {
        console.error('Error processing message:', error);
    }
}

// Track active tab
chrome.tabs.onActivated.addListener((activeInfo) => {
    currentTabId = activeInfo.tabId;
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (tab.active) {
        currentTabId = tabId;
    }
});

// Initialize extension
chrome.runtime.onInstalled.addListener(() => {
    console.log('Customer Service Automation Extension installed');

    // Set initial state
    chrome.storage.sync.set({
        extensionEnabled: true,
        lastUpdated: new Date().toISOString()
    });
});

// Load saved state
chrome.storage.sync.get(['extensionEnabled'], (result) => {
    if (result.extensionEnabled !== undefined) {
        extensionEnabled = result.extensionEnabled;
    }
});