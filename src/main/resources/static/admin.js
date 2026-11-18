const filters = document.querySelector('#filters');
const search = document.querySelector('#search');
const category = document.querySelector('#category');
const statusMessage = document.querySelector('#status');
const notice = document.querySelector('#notice');
const error = document.querySelector('#error');
const retry = document.querySelector('#retry');
const catalog = document.querySelector('#catalog');
const itemRows = document.querySelector('#items');
const add = document.querySelector('#add');

const dialog = document.querySelector('#item-dialog');
const form = document.querySelector('#item-form');
const dialogTitle = document.querySelector('#dialog-title');
const nameField = document.querySelector('#name-field');
const itemName = document.querySelector('#item-name');
const categoryFields = document.querySelector('#category-fields');
const categoryOptions = document.querySelector('#category-options');
const deleteQuestion = document.querySelector('#delete-question');
const formError = document.querySelector('#form-error');
const cancel = document.querySelector('#cancel');
const submit = document.querySelector('#submit');

const itemsUrl = '/api/admin/items';
let items = [];
let operation;

function renderItems() {
  const query = search.value.trim().toLowerCase();
  const categoryId = category.value;
  const matches = items.filter(item =>
    item.name.toLowerCase().includes(query) &&
    (!categoryId || item.categories.some(entry => entry.id === categoryId))
  );

  const rows = document.createDocumentFragment();
  for (const item of matches) {
    const row = document.createElement('tr');
    const name = document.createElement('th');
    name.scope = 'row';
    name.textContent = item.name;
    const categories = document.createElement('td');
    if (item.categories.length === 0) {
      categories.className = 'no-categories';
      categories.lang = 'en';
      categories.textContent = 'No categories';
    } else {
      const list = document.createElement('ul');
      list.className = 'category-list';
      for (const entry of item.categories) {
        const badge = document.createElement('li');
        badge.textContent = entry.name;
        list.append(badge);
      }
      categories.append(list);
    }

    const actions = document.createElement('td');
    const buttons = document.createElement('div');
    buttons.className = 'row-actions';
    buttons.append(
      actionButton('Изменить', 'secondary', item.name, 'edit', () => openDialog('edit', item)),
      actionButton('Удалить', 'delete-action', item.name, 'delete', () => openDialog('delete', item))
    );
    actions.append(buttons);
    row.append(name, categories, actions);
    rows.append(row);
  }

  itemRows.replaceChildren(rows);
  catalog.hidden = matches.length === 0;
  statusMessage.textContent = matches.length > 0
    ? 'Показано: ' + matches.length + ' из ' + items.length
    : items.length === 0 ? 'В каталоге пока нет вещей.' : 'Подходящие вещи не найдены.';
}

async function loadCatalog() {
  filters.disabled = true;
  add.disabled = true;
  retry.hidden = true;
  catalog.inert = true;
  error.textContent = '';
  statusMessage.textContent = 'Загрузка каталога…';
  try {
    const [loadedItems, categories] = await Promise.all([
      request(itemsUrl).then(response => response.json()),
      request('/api/admin/categories').then(response => response.json())
    ]);
    items = loadedItems;

    const selectedCategory = category.value;
    category.replaceChildren(new Option('Все категории', ''));
    categoryOptions.replaceChildren();
    for (const entry of categories) {
      category.add(new Option(entry.name, entry.id));
      const label = document.createElement('label');
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.name = 'categoryIds';
      checkbox.value = entry.id;
      label.append(checkbox, document.createTextNode(entry.name));
      categoryOptions.append(label);
    }
    if (categories.length === 0) {
      categoryOptions.textContent = 'В каталоге пока нет категорий.';
    }
    if (categories.some(entry => entry.id === selectedCategory)) {
      category.value = selectedCategory;
    }

    filters.disabled = false;
    add.disabled = false;
    renderItems();
    catalog.inert = false;
  } catch {
    catalog.hidden = true;
    statusMessage.textContent = '';
    error.textContent = 'Не удалось загрузить каталог.';
    retry.hidden = false;
  }
}

function openDialog(mode, item) {
  operation = { mode, item };
  const deleting = mode === 'delete';
  form.reset();
  formError.textContent = '';
  dialogTitle.textContent = deleting ? 'Удалить вещь?' : mode === 'create' ? 'Добавить вещь' : 'Изменить вещь';
  nameField.hidden = deleting;
  itemName.disabled = deleting;
  itemName.value = item?.name ?? '';
  categoryFields.hidden = deleting;
  if (mode === 'edit') {
    const selectedIds = new Set(item.categories.map(entry => entry.id));
    for (const checkbox of categoryOptions.querySelectorAll('input[type="checkbox"]')) {
      checkbox.checked = selectedIds.has(checkbox.value);
    }
  }
  deleteQuestion.hidden = !deleting;
  deleteQuestion.textContent = deleting ? 'Удалить вещь «' + item.name + '» из каталога?' : '';
  submit.textContent = deleting ? 'Удалить' : 'Сохранить';
  submit.classList.toggle('danger', deleting);
  dialog.showModal();
  (deleting ? cancel : itemName).focus();
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled) return;
  const { mode, item } = operation;
  const name = itemName.value.trim();
  if (mode !== 'delete' && (!name || [...name].length > 255)) {
    formError.textContent = !name ? 'Введите название вещи.' : 'Название должно содержать не более 255 символов.';
    itemName.focus();
    return;
  }
  const options = { method: 'DELETE' };
  if (mode !== 'delete') {
    const body = { name, categoryIds: new FormData(form).getAll('categoryIds') };
    options.method = mode === 'create' ? 'POST' : 'PUT';
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify(body);
  }

  formError.textContent = '';
  notice.textContent = '';
  submit.disabled = true;
  cancel.disabled = true;
  itemName.disabled = true;
  categoryFields.disabled = true;
  try {
    await request(item ? itemsUrl + '/' + encodeURIComponent(item.id) : itemsUrl, options);
    dialog.close();
    notice.textContent = mode === 'create' ? 'Вещь добавлена.' : mode === 'edit' ? 'Изменения сохранены.' : 'Вещь удалена.';
    await loadCatalog();
    (add.disabled ? retry : add).focus({ preventScroll: !add.disabled });
  } catch (failure) {
    formError.textContent = failure instanceof TypeError
      ? 'Не удалось связаться с сервером. Проверьте соединение и обновите список перед повторной попыткой.'
      : failure.message;
  } finally {
    submit.disabled = false;
    cancel.disabled = false;
    itemName.disabled = mode === 'delete';
    categoryFields.disabled = false;
  }
});

dialog.addEventListener('cancel', event => {
  if (submit.disabled) event.preventDefault();
});
cancel.addEventListener('click', () => dialog.close());
add.addEventListener('click', () => openDialog('create'));
retry.addEventListener('click', loadCatalog);
search.addEventListener('input', renderItems);
category.addEventListener('change', renderItems);
loadCatalog();
