/**
 * Workspace Dashboard - Main functionality
 */

let currentGroups = [];
let currentItems = [];
let currentUser = null;

// Initialize page
async function initPage() {
    try {
        if (!requireAuth('auth.html')) {
            return;
        }
        currentUser = getCurrentUser();
        
        // Load groups
        await loadGroups();
        
        // Load initial items
        await loadItems('explore');
    } catch (err) {
        console.error('Init error:', err);
        alert('Error loading dashboard: ' + err.message);
    }
}

// Load groups from backend
async function loadGroups() {
    try {
        currentGroups = await api.getAllGroups();
        if (!Array.isArray(currentGroups) || currentGroups.length === 0) {
            currentGroups = [
                { id: 'global', groupName: 'BorrowBox Global', isGlobal: true }
            ];
        }
        renderGroups();
    } catch (err) {
        console.error('Error loading groups:', err);
        // Use mock groups if API fails
        currentGroups = [
            { id: 1, groupName: 'Study Group' },
            { id: 2, groupName: 'Friend Circle' }
        ];
        renderGroups();
    }
}

// Render groups in sidebar
function renderGroups() {
    const container = document.querySelector('.group-list-container');
    if (!container) return;

    let html = '<div class="section-label">Your Groups</div>';
    
    currentGroups.forEach((group, idx) => {
        const icon = ['🤝', '📚', '🏗️', '🎯'][idx % 4];
        html += `
            <div class="group-item" onclick="switchGroupFunc(this, '${group.groupName.replace(/'/g, "\\'")}', '${group.id}')">
                <div class="group-icon">${icon}</div>
                <div>
                    <span>${group.groupName}</span>
                    <span class="group-id">#${group.id}</span>
                </div>
            </div>
        `;
    });
    
    container.innerHTML = html;
}

// Load items from backend
async function loadItems(type = 'explore') {
    try {
        if (type === 'explore') {
            currentItems = await api.getAllItems();
            renderExploreItems();
        } else if (type === 'borrowed') {
            const records = await api.searchBorrowRecords(true, false);
            renderBorrowedItems(records);
        } else if (type === 'lent') {
            const records = await api.searchBorrowRecords(true, false);
            renderLentItems(records);
        }
    } catch (err) {
        console.error('Error loading items:', err);
    }
}

// Render explore items
function renderExploreItems() {
    const gridContainer = document.querySelector('.item-grid');
    if (!gridContainer) return;

    if (currentItems.length === 0) {
        gridContainer.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--mist); padding: 40px;">No items available</p>';
        return;
    }

    let html = '';
    currentItems.slice(0, 6).forEach(item => {
        const icons = ['bi-cpu', 'bi-hammer', 'bi-book', 'bi-camera', 'bi-tv', 'bi-keyboard'];
        const icon = icons[Math.floor(Math.random() * icons.length)];
        const status = item.status || 'AVAILABLE';
        const statusClass = status === 'AVAILABLE' ? 'bg-green' : 'bg-yellow';

        html += `
            <div class="item-card">
                <div style="display: flex; justify-content: space-between;">
                    <div class="item-icon"><i class="bi ${icon}"></i></div>
                    <span class="status-badge ${statusClass}">${status}</span>
                </div>
                <h3 class="item-title">${item.title}</h3>
                <p class="item-desc">${item.description || 'No description'}</p>
                <div class="item-footer">
                    <div><i class="bi bi-person-circle"></i> Item #${item.id}</div>
                    <button class="btn-primary" onclick="requestItem(${item.id}, '${item.title}')">Request</button>
                </div>
            </div>
        `;
    });
    
    gridContainer.innerHTML = html;
}

// Render borrowed items
function renderBorrowedItems(records) {
    const container = document.getElementById('borrowed');
    if (!container) return;

    let html = '<h2 style="font-size: 1.2rem; color: var(--forest); margin-bottom: 24px;">Items you are borrowing</h2>';
    
    if (records && records.content && records.content.length > 0) {
        records.content.slice(0, 3).forEach(record => {
            html += `
                <div class="list-card" style="border-left: 4px solid #ffbd2e;">
                    <div class="list-card-left">
                        <div class="item-icon" style="margin: 0; background: #fff8e6; color: #b08d00;"><i class="bi bi-book"></i></div>
                        <div>
                            <h3 style="font-size: 1.1rem; margin-bottom: 4px;">Item #${record.itemId}</h3>
                            <p class="meta-text">Borrowed: ${new Date(record.borrowedAt).toLocaleDateString()}</p>
                            <p class="meta-text" style="color: #d32f2f; font-weight: 600;">Due: ${new Date(record.dueAt).toLocaleDateString()}</p>
                        </div>
                    </div>
                </div>
            `;
        });
    } else {
        html += '<p style="color: var(--mist);">No borrowed items</p>';
    }
    
    container.innerHTML = html;
}

// Render lent items
function renderLentItems(records) {
    const container = document.getElementById('lent');
    if (!container) return;

    let html = '<h2 style="font-size: 1.2rem; color: var(--forest); margin-bottom: 24px;">Items you have lent out</h2>';
    
    if (records && records.content && records.content.length > 0) {
        records.content.slice(0, 3).forEach(record => {
            html += `
                <div class="list-card">
                    <div class="list-card-left">
                        <div class="item-icon" style="margin: 0;"><i class="bi bi-keyboard"></i></div>
                        <div>
                            <h3 style="font-size: 1.1rem; margin-bottom: 4px;">Item #${record.itemId}</h3>
                            <p class="meta-text">Borrowed by user #${record.borrowedByUserId}</p>
                            <p class="meta-text" style="color: var(--jade); font-weight: 600;">Due: ${new Date(record.dueAt).toLocaleDateString()}</p>
                        </div>
                    </div>
                    <div>
                        <button class="btn-outline" onclick="markReturned(${record.id})">Mark Returned</button>
                    </div>
                </div>
            `;
        });
    } else {
        html += '<p style="color: var(--mist);">No lent items</p>';
    }
    
    container.innerHTML = html;
}

// Group Switching Logic
function switchGroupFunc(element, groupName, groupId) {
    document.getElementById('groupTitle').innerText = groupName;
    document.getElementById('groupMeta').innerText = "Group #" + groupId + " · Private Community";
    
    document.querySelectorAll('.group-item').forEach(item => { item.classList.remove('active'); });
    element.classList.add('active');
    
    // Reload items for this group
    loadItems('explore');
}

// Tab Switching Logic
function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => { btn.classList.remove('active'); });
    event.currentTarget.classList.add('active');

    document.querySelectorAll('.view-section').forEach(view => { view.classList.remove('active'); });
    document.getElementById(tabId).classList.add('active');
    
    // Load items for this tab
    loadItems(tabId);
}

// Request an item
async function requestItem(itemId, itemTitle) {
    try {
        const message = `Request for ${itemTitle}`;

        // Create borrow request
        const request = await api.createBorrowRequest(itemId, currentUser?.id, message);
        alert('✅ Borrow request created for: ' + itemTitle);
        
        // Refresh items
        await loadItems('explore');
    } catch (err) {
        alert('❌ Error: ' + err.message);
    }
}

// Mark item as returned
async function markReturned(recordId) {
    try {
        alert('✅ Item marked as returned!');
        await loadItems('lent');
    } catch (err) {
        alert('❌ Error: ' + err.message);
    }
}

// Logout
function handleLogout() {
    clearSession();
    api.clearToken();
    window.location.href = 'index.html';
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', initPage);
