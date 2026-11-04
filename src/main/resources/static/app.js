'use strict';
const $ = id => document.getElementById(id);
const storageKey = 'qrserial.batch.v1';
const titleStorageKey = 'qrserial.title.v1';
let serials = [], busy = false, pdfUrl = null, previewUrl = null;
// Recupera apenas lotes compatíveis com os limites aceitos pela API.
try {
  const saved = JSON.parse(localStorage.getItem(storageKey) || '[]');
  if (Array.isArray(saved) && saved.length <= 500 && saved.every(valid)) serials = saved;
  $('title').value = (localStorage.getItem(titleStorageKey) || '').slice(0, 120);
} catch { $('status').textContent = 'Erro ao recuperar lote.'; }
function valid(value) { return typeof value === 'string' && /^[\x20-\x7E]{1,120}$/.test(value) && value.trim().length > 0; }
function invalidatePdf() {
  // Uma alteração no lote invalida o download anterior e libera a memória do arquivo.
  if (pdfUrl) URL.revokeObjectURL(pdfUrl);
  pdfUrl = null;
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = null;
  $('preview-panel').hidden = true;
  $('preview-image').removeAttribute('src');
  $('download').hidden = true;
}
function render() {
  $('list').replaceChildren();
  serials.forEach((serial, index) => {
    const item = document.createElement('li');
    const text = document.createElement('span');
    text.textContent = `${String(index + 1).padStart(2, '0')} · ${serial}`;
    const remove = document.createElement('button');
    remove.textContent = 'Remover';
    remove.disabled = busy;
    remove.setAttribute('aria-label', `Remover serial ${serial}`);
    remove.onclick = () => {
      serials.splice(index, 1);
      $('status').textContent = `Removido: ${serial}`;
      save();
      $('serial').focus();
    };
    item.append(text, remove); $('list').append(item);
  });
  $('count').textContent = serials.length;
  $('count').setAttribute('aria-label', `${serials.length} seriais`);
  $('empty').hidden = serials.length > 0;
  $('finish').disabled = busy || !serials.length;
  $('preview').disabled = busy || !serials.length;
  $('print').disabled = busy || !serials.length;
  $('new-batch').disabled = busy || !serials.length;
  $('serial').disabled = busy;
  $('title').disabled = busy;
  $('scan-form').querySelector('button').disabled = busy;
}
function save() {
  invalidatePdf();
  try {
    localStorage.setItem(storageKey, JSON.stringify(serials));
    localStorage.setItem(titleStorageKey, $('title').value);
  }
  catch { $('status').textContent += ' Lote não salvo. Mantenha a página aberta.'; }
  render();
}
// Leitores em modo teclado enviam Enter para finalizar o serial e submeter o formulário.
$('scan-form').onsubmit = event => {
  event.preventDefault();
  if (busy) return;
  const value = $('serial').value;
  if (!value) return;
  if (serials.length >= 500) { $('status').textContent = 'Limite: 500 seriais.'; return; }
  if (!valid(value)) { $('status').textContent = 'Serial inválido.'; return; }
  if (serials.includes(value)) {
    $('status').textContent = 'Serial duplicado.';
    $('serial').value = '';
    return;
  }
  serials.push(value);
  $('status').textContent = `Registrado: ${value}`;
  $('serial').value = '';
  save();
  $('serial').focus();
};
// O título faz parte do arquivo: editá-lo também invalida o PDF anterior.
$('title').addEventListener('input', save);
$('new-batch').onclick = () => {
  if (confirm('Apagar lote atual?')) {
    serials = []; $('title').value = ''; $('status').textContent = 'Lote limpo.'; save(); $('serial').focus();
  }
};
async function requestFile(path) {
  const response = await fetch(path, {method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({serials, title: $('title').value})});
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || 'Erro ao gerar arquivo.');
  }
  return response;
}
async function preparePreview() {
  if (!previewUrl) {
    const response = await requestFile('/api/print/preview');
    previewUrl = URL.createObjectURL(await response.blob());
  }
  $('preview-image').src = previewUrl;
  $('preview-panel').hidden = false;
}
async function run(action) {
  if (busy || !serials.length) return;
  busy = true; render(); $('status').textContent = 'Gerando…';
  try { await action(); }
  catch (error) { $('status').textContent = error instanceof TypeError ? 'Falha de conexão.' : error.message; }
  finally { busy = false; render(); }
}
$('preview').onclick = () => run(async () => {
  await preparePreview();
  $('preview-panel').scrollIntoView({behavior: 'smooth', block: 'start'});
  $('status').textContent = 'Prévia pronta.';
});
$('close-preview').onclick = () => { $('preview-panel').hidden = true; };
$('print').onclick = () => run(async () => {
  await preparePreview();
  // Imprime a imagem do próprio PDF em A4, inclusive sem visualizador de PDF instalado.
  const frame = $('print-frame');
  const doc = frame.contentDocument;
  doc.open();
  doc.write('<!doctype html><html><head><title></title><style>@page{size:A4 portrait;margin:0}html,body{margin:0;padding:0}img{display:block;width:210mm;height:297mm}</style></head><body></body></html>');
  doc.close();
  doc.title = $('title').value || 'seriais';
  const img = doc.createElement('img');
  const loaded = new Promise((resolve, reject) => {
    img.onload = resolve;
    img.onerror = () => reject(new Error('Erro ao carregar impressão.'));
  });
  img.src = previewUrl;
  doc.body.append(img);
  await loaded;
  frame.contentWindow.focus();
  frame.contentWindow.print();
  $('status').textContent = 'Impressão solicitada.';
});
$('finish').onclick = () => run(async () => {
  if (!pdfUrl) {
    const response = await requestFile('/api/print');
    pdfUrl = URL.createObjectURL(await response.blob());
    const disposition = response.headers.get('Content-Disposition') || '';
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    $('download').download = encodedName ? decodeURIComponent(encodedName[1]) : 'seriais.pdf';
    $('download').href = pdfUrl;
    $('download').hidden = false;
  }
  $('download').click();
  $('status').textContent = 'PDF gerado.';
});
render();

