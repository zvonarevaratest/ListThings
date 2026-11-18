const svgNamespace = 'http://www.w3.org/2000/svg';
const actionIcons = {
  edit: 'M13.5 6.5l4 4M4 20l4.5-1 11-11a2.8 2.8 0 0 0-4-4l-11 11L4 20z',
  delete: 'M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7'
};

function actionButton(text, className, name, mode, onClick) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = className;
  button.title = text;
  button.setAttribute('aria-label', text + ' «' + name + '»');
  const icon = document.createElementNS(svgNamespace, 'svg');
  icon.setAttribute('viewBox', '0 0 24 24');
  icon.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(svgNamespace, 'path');
  path.setAttribute('d', actionIcons[mode]);
  icon.append(path);
  button.append(icon);
  button.addEventListener('click', onClick);
  return button;
}

async function request(url, options = {}) {
  const response = await fetch(url, { cache: 'no-store', ...options });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || 'Не удалось выполнить запрос. Попробуйте ещё раз.');
  }
  return response;
}
