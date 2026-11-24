const filters = document.querySelector('#filters');
const search = document.querySelector('#search');
const typeFilter = document.querySelector('#type-filter');
const statusMessage = document.querySelector('#status');
const notice = document.querySelector('#notice');
const error = document.querySelector('#error');
const retry = document.querySelector('#retry');
const catalog = document.querySelector('#catalog');
const categoryRows = document.querySelector('#categories');
const add = document.querySelector('#add');

const dialog = document.querySelector('#category-dialog');
const form = document.querySelector('#category-form');
const dialogTitle = document.querySelector('#dialog-title');
const editFields = document.querySelector('#edit-fields');
const categoryName = document.querySelector('#category-name');
const categoryType = document.querySelector('#category-type');
const itemPicker = document.querySelector('#item-picker');
const itemSearch = document.querySelector('#item-search');
const itemOptions = document.querySelector('#item-options');
const itemSearchEmpty = document.querySelector('#item-search-empty');
const selectedItemsCount = document.querySelector('#selected-items-count');
const clearItems = document.querySelector('#clear-items');
const deleteQuestion = document.querySelector('#delete-question');
const formError = document.querySelector('#form-error');
const cancel = document.querySelector('#cancel');
const submit = document.querySelector('#submit');

const categoriesUrl = '/api/admin/categories';
const activities = location.pathname === '/admin/activities';
const activityType = 'ACTIVITY';
let categories = [];
let operation;

if (activities) {
  document.title = 'ListThings — цели поездки';
  document.querySelector('h1').textContent = 'Цели поездки';
  document.querySelector('.admin-nav [aria-current]').removeAttribute('aria-current');
  document.querySelector('.admin-nav a[href="/admin/activities"]').setAttribute('aria-current', 'page');
  document.querySelector('#search-label').textContent = 'Название цели';
  document.querySelector('#name-label').textContent = 'Название цели';
  document.querySelector('#item-picker-title').textContent = 'Вещи для этой цели';
  document.querySelector('#filters legend').textContent = 'Поиск';
  document.querySelector('#catalog thead th').textContent = 'Цель поездки';
  document.querySelector('#catalog thead th:nth-child(2)').hidden = true;
  catalog.setAttribute('aria-label', 'Цели поездки и количество вещей');
  typeFilter.closest('label').hidden = true;
  categoryType.closest('label').hidden = true;
  categoryType.disabled = true;
  add.textContent = '+ Добавить цель';
  statusMessage.textContent = 'Загрузка целей поездки…';
} else {
  for (const type of ['UNIVERSAL', 'TRANSPORT', 'ACCOMMODATION']) {
    typeFilter.add(new Option(type, type));
    categoryType.add(new Option(type, type));
  }
}

function renderCategories() {
  const query = search.value.trim().toLowerCase();
  const type = typeFilter.value;
  const matches = categories.filter(entry =>
    entry.name.toLowerCase().includes(query) && (!type || entry.type === type)
  );

  const rows = document.createDocumentFragment();
  for (const entry of matches) {
    const row = document.createElement('tr');
    const name = document.createElement('th');
    name.scope = 'row';
    name.textContent = entry.name;
    const typeCell = document.createElement('td');
    typeCell.textContent = entry.type;
    typeCell.hidden = activities;
    const count = document.createElement('td');
    count.textContent = entry.itemCount;
    const actions = document.createElement('td');
    const buttons = document.createElement('div');
    buttons.className = 'row-actions';
    buttons.append(
      actionButton('Изменить', 'secondary', entry.name, 'edit', () => openDialog('edit', entry)),
      actionButton('Удалить', 'delete-action', entry.name, 'delete', () => openDialog('delete', entry))
    );
    actions.append(buttons);
    row.append(name, typeCell, count, actions);
    rows.append(row);
  }

  categoryRows.replaceChildren(rows);
  catalog.hidden = matches.length === 0;
  statusMessage.textContent = matches.length > 0
    ? 'Показано: ' + matches.length + ' из ' + categories.length
    : categories.length === 0
      ? (activities ? 'Цели поездки пока не добавлены.' : 'В каталоге пока нет категорий.')
      : (activities ? 'Подходящие цели не найдены.' : 'Подходящие категории не найдены.');
}

async function loadCategories() {
  filters.disabled = true;
  add.disabled = true;
  retry.hidden = true;
  catalog.inert = true;
  error.textContent = '';
  statusMessage.textContent = activities ? 'Загрузка целей поездки…' : 'Загрузка категорий…';
  try {
    const response = await request(categoriesUrl);
    categories = (await response.json()).filter(entry => (entry.type === activityType) === activities);
    filters.disabled = false;
    add.disabled = false;
    renderCategories();
    catalog.inert = false;
  } catch {
    catalog.hidden = true;
    statusMessage.textContent = '';
    error.textContent = activities ? 'Не удалось загрузить цели поездки.' : 'Не удалось загрузить категории.';
    retry.hidden = false;
  }
}

function renderItemOptions(items, categoryId) {
  const options = document.createDocumentFragment();
  const byName = new Intl.Collator('ru');
  items.sort((a, b) => byName.compare(a.name, b.name));
  for (const item of items) {
    const label = document.createElement('label');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.name = 'itemIds';
    checkbox.value = item.id;
    checkbox.checked = item.categories.some(link => link.id === categoryId);
    label.append(checkbox, document.createTextNode(item.name));
    options.append(label);
  }
  itemOptions.replaceChildren(options);
  itemOptions.scrollTop = 0;
  filterItemOptions();
  updateSelectedItemsCount();
}

function filterItemOptions() {
  const query = itemSearch.value.trim().toLowerCase();
  let found = false;
  for (const label of itemOptions.children) {
    const matches = label.textContent.toLowerCase().includes(query);
    label.hidden = !matches;
    found ||= matches;
  }
  itemOptions.hidden = !found;
  itemSearchEmpty.hidden = found;
  itemSearchEmpty.textContent = itemOptions.children.length
    ? 'Подходящие вещи не найдены.'
    : 'В каталоге пока нет вещей.';
}

function updateSelectedItemsCount() {
  const count = itemOptions.querySelectorAll('input:checked').length;
  selectedItemsCount.textContent = 'Выбрано вещей: ' + count;
  clearItems.disabled = count === 0;
}

async function openDialog(mode, entry) {
  const currentOperation = { mode, entry };
  operation = currentOperation;
  const deleting = mode === 'delete';
  form.reset();
  formError.textContent = '';
  const subject = activities ? 'цель' : 'категорию';
  dialogTitle.textContent = deleting ? 'Удалить ' + subject + '?' : (mode === 'create' ? 'Добавить ' : 'Изменить ') + subject;
  editFields.hidden = deleting;
  editFields.disabled = deleting;
  categoryName.value = entry?.name ?? '';
  categoryType.value = entry?.type ?? '';
  deleteQuestion.hidden = !deleting;
  deleteQuestion.textContent = deleting
    ? 'Удалить ' + subject + ' «' + entry.name + '»? Связи будут удалены, а вещи останутся в каталоге.'
    : '';
  submit.textContent = deleting ? 'Удалить' : 'Сохранить';
  submit.classList.toggle('danger', deleting);
  submit.disabled = !deleting;
  if (!deleting) {
    itemPicker.disabled = true;
    itemOptions.replaceChildren();
    itemOptions.hidden = true;
    selectedItemsCount.textContent = '';
    itemSearchEmpty.hidden = false;
    itemSearchEmpty.textContent = 'Загрузка вещей…';
  }
  dialog.showModal();
  (deleting ? cancel : categoryName).focus();
  if (deleting) return;

  try {
    const response = await request('/api/admin/items');
    const items = await response.json();
    if (!dialog.open || operation !== currentOperation) return;
    renderItemOptions(items, entry?.id);
    itemPicker.disabled = false;
    submit.disabled = false;
  } catch {
    if (!dialog.open || operation !== currentOperation) return;
    itemSearchEmpty.textContent = 'Не удалось загрузить вещи. Откройте форму заново.';
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled) return;
  const { mode, entry } = operation;
  const name = categoryName.value.trim();
  if (mode !== 'delete' && (!name || [...name].length > 100)) {
    const entity = activities ? 'цели' : 'категории';
    formError.textContent = !name ? 'Введите название ' + entity + '.'
      : 'Название ' + entity + ' должно содержать не более 100 символов.';
    categoryName.focus();
    return;
  }

  const options = { method: 'DELETE' };
  if (mode !== 'delete') {
    options.method = mode === 'create' ? 'POST' : 'PUT';
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify({
      name,
      type: activities ? activityType : categoryType.value,
      itemIds: new FormData(form).getAll('itemIds')
    });
  }

  formError.textContent = '';
  notice.textContent = '';
  submit.disabled = true;
  cancel.disabled = true;
  editFields.disabled = true;
  try {
    await request(entry ? categoriesUrl + '/' + encodeURIComponent(entry.id) : categoriesUrl, options);
    dialog.close();
    notice.textContent = mode === 'edit' ? 'Изменения сохранены.'
      : (activities ? 'Цель' : 'Категория') + (mode === 'create' ? ' добавлена.' : ' удалена.');
    await loadCategories();
    (add.disabled ? retry : add).focus({ preventScroll: !add.disabled });
  } catch (failure) {
    formError.textContent = failure instanceof TypeError
      ? 'Не удалось связаться с сервером. Проверьте соединение и обновите список перед повторной попыткой.'
      : failure.message;
  } finally {
    submit.disabled = false;
    cancel.disabled = false;
    editFields.disabled = mode === 'delete';
  }
});

dialog.addEventListener('cancel', event => {
  if (cancel.disabled) event.preventDefault();
});
cancel.addEventListener('click', () => dialog.close());
add.addEventListener('click', () => openDialog('create'));
retry.addEventListener('click', loadCategories);
search.addEventListener('input', renderCategories);
typeFilter.addEventListener('change', renderCategories);
loadCategories();

itemSearch.addEventListener('input', filterItemOptions);
itemOptions.addEventListener('change', updateSelectedItemsCount);
clearItems.addEventListener('click', () => {
  for (const checkbox of itemOptions.querySelectorAll('input:checked')) {
    checkbox.checked = false;
  }
  updateSelectedItemsCount();
  itemSearch.focus();
});
