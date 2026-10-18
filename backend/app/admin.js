'use strict';
// Local-only demonstration admin UI. Key remains in memory, never in localStorage.
const el = id => document.getElementById(id);
const fields = ['name','type','payloadKg','cargoVolumeM3','hourlyPriceRub','dailyPriceRub','transmission','pickupLocation','description','imageUrl','latitude','longitude','isPublished'];
let selectedId = null;
let fleet = [];
let key = '';

function message(value, error=false) {
  el('status').textContent = value;
  el('status').className = error ? 'status error' : 'status';
}

async function api(path, method='GET', body=null) {
  const options = { method, headers: {'X-Admin-Key': key, 'Accept':'application/json'}, cache:'no-store' };
  if(body) { options.body = JSON.stringify(body); options.headers['Content-Type']='application/json'; }
  const response = await fetch(path, options);
  if(!response.ok) {
    const payload = await response.json().catch(()=>({}));
    throw new Error(response.status === 401 ? 'Неверный ключ администратора' :
      (typeof payload.detail === 'string' ? payload.detail : `Ошибка: ${response.status}`));
  }
  return await response.json();
}

function renderList() {
  const root = el('cars');
  root.replaceChildren();
  if(!fleet.length) { const p = document.createElement('p'); p.className='empty'; p.textContent='Автомобилей пока нет'; root.append(p); return; }
  fleet.forEach(v => {
    const item = document.createElement('div'); item.className = 'car' + (selectedId === v.id ? ' active' : '');
    const info = document.createElement('div');
    const title = document.createElement('strong'); title.textContent=v.name;
    const sub = document.createElement('span'); sub.textContent=`${v.type} · ${v.dailyPriceRub.toLocaleString('ru-RU')} ₽/сутки · ${v.isPublished ? 'В каталоге' : 'Скрыт'}`;
    const btn = document.createElement('button'); btn.className='secondary'; btn.type='button'; btn.textContent='Изменить'; btn.addEventListener('click',()=>openForm(v));
    info.append(title,sub); item.append(info,btn); root.append(item);
  });
}

async function reload() {
  fleet = (await api('/v1/admin/vehicles')).data;
  renderList();
}

function openForm(v) {
  selectedId = v ? v.id : null;
  el('edit').hidden=false;
  el('formEmpty').hidden=true;
  el('formTitle').textContent=v ? 'Редактирование автомобиля' : 'Новый автомобиль';
  fields.forEach(field => { if(field === 'isPublished') el(field).checked = v ? v[field] : true;
    else el(field).value = v?.[field] ?? (['description','imageUrl','latitude','longitude'].includes(field) ? '' : ''); });
  renderList(); message('');
}

function formPayload() {
  const out = {};
  fields.forEach(field => {
    if(field === 'isPublished') out[field]=el(field).checked;
    else if(['latitude','longitude'].includes(field)) out[field]=el(field).value.trim() === '' ? null : Number(el(field).value);
    else if(['payloadKg','cargoVolumeM3','hourlyPriceRub','dailyPriceRub'].includes(field)) out[field]=Number(el(field).value);
    else out[field]=el(field).value.trim();
  });
  return out;
}

async function reloadBookings() {
  const root=el('bookings');
  const data=(await api('/v1/admin/bookings')).data;
  root.replaceChildren();
  if (!data.length) {
    const p=document.createElement('p'); p.className='empty'; p.textContent='Бронирований пока нет'; root.append(p);
    return;
  }
  const label = {PENDING:'Ожидает подтверждения', CONFIRMED:'Подтверждено',
    ACTIVE:'Выдан (демо)', COMPLETED:'Возвращён (демо)', CANCELLED:'Отменено', EXPIRED:'Истекло'};
  data.forEach(b=>{
    const line=document.createElement('div'); line.className='car';
    const info=document.createElement('div');
    const title=document.createElement('strong');
    const car=fleet.find(v=>v.id===b.vehicleId);
    title.textContent=car?.name || b.vehicleId;
    const detail=document.createElement('span');
    const date=new Date(b.startAt);
    detail.textContent=`${label[b.status] ?? b.status} · ${Number(b.totalRub).toLocaleString('ru-RU')} ₽ · ${isNaN(date) ? b.startAt : date.toLocaleString('ru-RU')}`;
    info.append(title,detail); line.append(info);
    const actions = {
      PENDING: {route: 'confirm', title: 'Подтвердить заявку'},
      CONFIRMED: {route: 'handover', title: 'Отметить выдачу (демо)'},
      ACTIVE: {route: 'return', title: 'Отметить возврат (демо)'},
    };
    const action=actions[b.status];
    if(action) {
      const btn=document.createElement('button'); btn.textContent=action.title;
      btn.addEventListener('click',async()=>{
        if (action.route !== 'confirm' && !window.confirm(
          'Это только отметка в демобазе. Реальные двери, документы и платежи не управляются. Продолжить?'
        )) return;
        btn.disabled=true;
        try {
          await api(`/v1/admin/bookings/${encodeURIComponent(b.id)}/${action.route}`,'POST');
          await reloadBookings();
          message('Статус демозаявки изменён');
        } catch(e){message(e.message,true)} finally{btn.disabled=false}
      });
      line.append(btn);
    }
    root.append(line);
  });
}

el('refreshBookings').addEventListener('click',async()=>{
  try {await reloadBookings(); message('История обновлена')} catch(e){message(e.message,true)}
});

el('connect').addEventListener('click', async()=>{
  key = el('key').value;
  if (!key) { message('Введите ключ администратора',true); return; }
  try { await reload(); await reloadBookings(); el('add').disabled=false; el('refreshBookings').disabled=false; message('Автопарк и история загружены'); }
  catch(e) { el('add').disabled=true; el('refreshBookings').disabled=true; message(e.message,true); }
});
el('add').addEventListener('click',()=>openForm(null));
el('cancel').addEventListener('click',()=>{
  el('edit').hidden=true; el('formEmpty').hidden=false; selectedId=null; renderList(); message('');
});
el('edit').addEventListener('submit', async event=>{
  event.preventDefault();
  const save=el('save'); save.disabled=true;
  try {
    const payload=formPayload();
    const result = selectedId ? await api(`/v1/admin/vehicles/${encodeURIComponent(selectedId)}`,'PATCH',payload)
      : await api('/v1/admin/vehicles','POST',payload);
    await reload(); openForm(result); message('Изменения сохранены. Каталог обновится при следующей загрузке приложения.');
  } catch(e) {message(e.message,true)} finally {save.disabled=false}
});
