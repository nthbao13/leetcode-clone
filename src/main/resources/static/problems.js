// Hardcoded problem metadata (API /problems has circular-ref issue)
// id must match backend problem ID
const PROBLEMS = [
  { id: 1, title: 'Two Sum',         difficulty: 'Easy',   tag: 'Array / Hash Map' },
];

// Track solved status in localStorage
function isSolved(id) {
  return localStorage.getItem(`solved_${id}`) === '1';
}

function render() {
  const tbody = document.getElementById('problem-list');
  if (!PROBLEMS.length) {
    tbody.innerHTML = `<tr><td colspan="5" style="color:var(--text-muted);text-align:center;padding:32px">No problems yet</td></tr>`;
    return;
  }

  tbody.innerHTML = PROBLEMS.map(p => {
    const diffClass = { Easy: 'badge-easy', Medium: 'badge-medium', Hard: 'badge-hard' }[p.difficulty] ?? '';
    const solved    = isSolved(p.id);
    return `
      <tr onclick="location.href='problem.html?id=${p.id}'">
        <td class="td-status"><span class="status-dot ${solved ? 'solved' : ''}"></span></td>
        <td class="td-num">${p.id}</td>
        <td class="td-title"><a href="problem.html?id=${p.id}">${p.title}</a></td>
        <td class="td-tag"><span class="tag">${p.tag}</span></td>
        <td class="td-diff"><span class="${diffClass}">${p.difficulty}</span></td>
      </tr>
    `;
  }).join('');
}

render();
