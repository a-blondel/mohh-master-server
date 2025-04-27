// Store the original data for resetting
let originalData = [];

var vers = "PSP/MOH07";

// Fetch and display leaderboard data
async function fetchLeaderboard() {
    try {
        let allPlayers = [];
        let offset = 0;
        const limit = 100;
        let hasMorePlayers = true;

        while (hasMorePlayers) {
            const response = await fetch(`/api/leaderboard?vers=${vers}&offset=${offset}&limit=${limit}`);
            const data = await response.json();
            
            if (data.length === 0) {
                hasMorePlayers = false;
            } else {
                allPlayers = allPlayers.concat(data);
                offset += limit;
                
                // If we got less than the limit, we've reached the end
                if (data.length < limit) {
                    hasMorePlayers = false;
                }
            }
        }

        originalData = [...allPlayers]; // Store original data
        displayLeaderboard(allPlayers);
    } catch (error) {
        console.error('Error fetching leaderboard:', error);
        document.querySelector('tbody').innerHTML = '<tr><td colspan="19">Error loading leaderboard data</td></tr>';
    }
}

// Calculate KPM and DPM
function calculateRates(kills, deaths, playTimeSeconds) {
    const playTimeMinutes = playTimeSeconds / 60;
    const kpm = (kills / playTimeMinutes).toFixed(2);
    const dpm = (deaths / playTimeMinutes).toFixed(2);
    return { kpm, dpm };
}

// Extract map name without game mode
function getMapName(mapWithMode) {
    return mapWithMode.split(':')[0].trim();
}

// Display leaderboard data in the table
function displayLeaderboard(data) {
    const tbody = document.querySelector('tbody');
    tbody.innerHTML = '';
    const optionalStatsToggle = document.getElementById('optional-stats-toggle');
    const showOptional = optionalStatsToggle && optionalStatsToggle.checked;

    // Update table headers
    const theadRow = document.querySelector('thead tr');
    theadRow.innerHTML = `
        <th>Rank</th>
        <th>Username</th>
        <th>Score</th>
        <th>Kills</th>
        <th>Deaths</th>
        <th>K/D Ratio</th>
        ${showOptional ? '<th>Accuracy</th>' : ''}
        ${showOptional ? '<th>KPM</th>' : ''}
        ${showOptional ? '<th>DPM</th>' : ''}
        ${showOptional ? '<th>Headshots</th>' : ''}
        <th>Play Time</th>
        <th>Wins</th>
        <th>Losses</th>
        ${showOptional ? '<th>Fav. Map</th>' : ''}
        ${showOptional ? '<th>Fav. Team</th>' : ''}
        ${showOptional ? '<th>Deathmatch</th>' : ''}
        ${showOptional ? '<th>Team DM</th>' : ''}
        ${showOptional ? '<th>Domination</th>' : ''}
        ${showOptional ? '<th>Demolition</th>' : ''}
        ${showOptional ? '<th>Hold the Line</th>' : ''}
        ${showOptional ? '<th>Battle Lines</th>' : ''}
        ${showOptional ? '<th>Infiltration</th>' : ''}
    `;

    // Re-add sorting functionality to headers
    addSorting();

    // Store the data globally for sorting
    window.leaderboardData = {};
    data.forEach(player => {
        window.leaderboardData[player.username] = player;
    });

    data.forEach(player => {
        const kdRatio = player.deaths === 0 ? player.kills : (player.kills / player.deaths).toFixed(2);
        const score = player.kills - player.deaths;
        const { kpm, dpm } = calculateRates(player.kills, player.deaths, player.playTime);
        const mapName = getMapName(player.mostPlayedMap);
        let rowHtml = `
            <td>${player.rank}</td>
            <td>${player.username}</td>
            <td>${score.toLocaleString()}</td>
            <td>${player.kills.toLocaleString()}</td>
            <td>${player.deaths.toLocaleString()}</td>
            <td>${kdRatio}</td>
        `;
        if (showOptional) rowHtml += `<td>${player.accuracy.toFixed(2)}%</td>`;
        if (showOptional) rowHtml += `<td>${kpm}</td>`;
        if (showOptional) rowHtml += `<td>${dpm}</td>`;
        if (showOptional) rowHtml += `<td>${player.headshots.toLocaleString()}</td>`;
        rowHtml += `<td>${player.playTimeString}</td>`;
        rowHtml += `<td>${player.wins.toLocaleString()}</td>`;
        rowHtml += `<td>${player.losses.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${mapName}</td>`;
        if (showOptional) rowHtml += `<td>${player.favoriteTeam}</td>`;
        if (showOptional) rowHtml += `<td>${player.dmGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.tdmGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.domGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.demGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.htlGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.blGames.toLocaleString()}</td>`;
        if (showOptional) rowHtml += `<td>${player.infGames.toLocaleString()}</td>`;
        const row = document.createElement('tr');
        row.innerHTML = rowHtml;
        tbody.appendChild(row);
    });
}

// Add sorting functionality
function addSorting() {
    const headers = document.querySelectorAll('th');
    headers.forEach((header, index) => {
        header.style.cursor = 'pointer';
        header.addEventListener('click', () => {
            sortTable(index);
        });
    });
}

// Sort table by column
function sortTable(columnIndex) {
    const tbody = document.querySelector('tbody');
    const rows = Array.from(tbody.querySelectorAll('tr'));
    const header = document.querySelectorAll('th')[columnIndex];
    const isAscending = header.classList.contains('asc');
    
    // Remove all sort classes
    document.querySelectorAll('th').forEach(th => {
        th.classList.remove('asc', 'desc');
    });
    
    // Add sort class to clicked header
    header.classList.add(isAscending ? 'desc' : 'asc');
    
    // Get the column name for special handling
    const columnName = header.textContent.trim();
    
    // Sort rows
    rows.sort((a, b) => {
        const aValue = a.cells[columnIndex].textContent;
        const bValue = b.cells[columnIndex].textContent;
        
        // Special handling for playtime column
        if (columnName === 'Play Time') {
            const aData = window.leaderboardData[a.cells[1].textContent]; // Get data by username
            const bData = window.leaderboardData[b.cells[1].textContent];
            return isAscending ? 
                bData.playTime - aData.playTime : 
                aData.playTime - bData.playTime;
        }
        
        // Handle numeric values
        if (!isNaN(aValue) && !isNaN(bValue)) {
            return isAscending ? 
                parseFloat(bValue) - parseFloat(aValue) : 
                parseFloat(aValue) - parseFloat(bValue);
        }
        
        // Handle string values
        return isAscending ? 
            bValue.localeCompare(aValue) : 
            aValue.localeCompare(bValue);
    });
    
    // Reorder rows
    rows.forEach(row => tbody.appendChild(row));
}

// Theme switching functionality
function initThemeToggle() {
    const themeToggle = document.getElementById('theme-toggle');
    const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');
    
    // Set initial theme based on system preference or saved preference
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark' || (!savedTheme && prefersDarkScheme.matches)) {
        document.documentElement.setAttribute('data-theme', 'dark');
        themeToggle.checked = true;
    }
    
    // Handle theme toggle
    themeToggle.addEventListener('change', (e) => {
        if (e.target.checked) {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('theme', 'light');
        }
    });
}

// Reset sorting to default (by rank)
function resetSorting() {
    // Remove all sort classes
    document.querySelectorAll('th').forEach(th => {
        th.classList.remove('asc', 'desc');
    });
    
    // Get the current optional stats state
    const optionalStatsToggle = document.getElementById('optional-stats-toggle');
    const showOptional = optionalStatsToggle && optionalStatsToggle.checked;
    
    // Display original data with current optional stats state
    displayLeaderboard(originalData);
}

// Initialize the leaderboard
document.addEventListener('DOMContentLoaded', () => {
    fetchLeaderboard();
    addSorting();
    initThemeToggle();
    initOptionalStatsToggle();
    initVersionSelect();
    
    // Add reset button handler
    document.getElementById('reset-sort').addEventListener('click', resetSorting);
});

// Initialize optional stats toggle
function initOptionalStatsToggle() {
    const optionalStatsToggle = document.getElementById('optional-stats-toggle');
    optionalStatsToggle.addEventListener('change', () => {
        // Update originalData when optional stats are toggled
        originalData = [...window.leaderboardData ? Object.values(window.leaderboardData) : []];
        displayLeaderboard(originalData);
    });
}


// Initialize version select
function initVersionSelect() {
    const versionSelect = document.getElementById('version-select');
    versionSelect.value = vers; // Set initial value
    
    versionSelect.addEventListener('change', (e) => {
        vers = e.target.value;
        fetchLeaderboard(); // Reload data with new version
    });
}

