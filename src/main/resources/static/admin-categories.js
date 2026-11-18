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
const deleteQuestion = document.querySelector('#delete-question');
const formError = document.querySelector('#form-error');
const cancel = document.querySelector('#cancel');
const submit = document.querySelector('#submit');

const categoriesUrl = '/api/admin/categories';
let categories = [];
let operation;

for (const type of ['UNIVERSAL', 'TRANSPORT', 'ACCOMMODATION']) {
  typeFilter.add(new Option(type, type));
  categoryType.add(new Option(type, type));
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
    : categories.length === 0 ? 'В каталоге пока нет категорий.' : 'Подходящие категории не найдены.';
}

async function loadCategories() {
  filters.disabled = true;
  add.disabled = true;
  retry.hidden = true;
  catalog.inert = true;
  error.textContent = '';
  statusMessage.textContent = 'Загрузка категорий…';
  try {
    const response = await request(categoriesUrl);
    categories = await response.json();
    filters.disabled = false;
    add.disabled = false;
    renderCategories();
    catalog.inert = false;
  } catch {
    catalog.hidden = true;
    statusMessage.textContent = '';
    error.textContent = 'Не удалось загрузить категории.';
    retry.hidden = false;
  }
}

function openDialog(mode, entry) {
  operation = { mode, entry };
  const deleting = mode === 'delete';
  form.reset();
  formError.textContent = '';
  dialogTitle.textContent = deleting ? 'Удалить категорию?' : mode === 'create' ? 'Добавить категорию' : 'Изменить категорию';
  editFields.hidden = deleting;
  editFields.disabled = deleting;
  categoryName.value = entry?.name ?? '';
  categoryType.value = entry?.type ?? '';
  deleteQuestion.hidden = !deleting;
  deleteQuestion.textContent = deleting
    ? 'Удалить категорию «' + entry.name + '»? Связи будут удалены, а вещи останутся в каталоге.'
    : '';
  submit.textContent = deleting ? 'Удалить' : 'Сохранить';
  submit.classList.toggle('danger', deleting);
  dialog.showModal();
  (deleting ? cancel : categoryName).focus();
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled) return;
  const { mode, entry } = operation;
  const name = categoryName.value.trim();
  if (mode !== 'delete' && (!name || [...name].length > 100)) {
    formError.textContent = !name ? 'Введите название категории.' : 'Название категории должно содержать не более 100 символов.';
    categoryName.focus();
    return;
  }

  const options = { method: 'DELETE' };
  if (mode !== 'delete') {
    options.method = mode === 'create' ? 'POST' : 'PUT';
    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify({ name, type: categoryType.value });
  }

  formError.textContent = '';
  notice.textContent = '';
  submit.disabled = true;
  cancel.disabled = true;
  editFields.disabled = true;
  try {
    await request(entry ? categoriesUrl + '/' + encodeURIComponent(entry.id) : categoriesUrl, options);
    dialog.close();
    notice.textContent = mode === 'create' ? 'Категория добавлена.' : mode === 'edit' ? 'Изменения сохранены.' : 'Категория удалена.';
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
  if (submit.disabled) event.preventDefault();
});
cancel.addEventListener('click', () => dialog.close());
add.addEventListener('click', () => openDialog('create'));
retry.addEventListener('click', loadCategories);
search.addEventListener('input', renderCategories);
typeFilter.addEventListener('change', renderCategories);
loadCategories();
