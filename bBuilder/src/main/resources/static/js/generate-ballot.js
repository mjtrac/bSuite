/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 * generate-ballot.js — handles the Generate PDF button on print/form.html
 */

function generateBallot() {
  const btn      = document.getElementById('generateBtn');
  const comboId  = document.getElementById('combinationId').value;
  const copies   = document.getElementById('copies')?.value || '1';
  const lang     = document.getElementById('langSelect')?.value || 'en';
  const csrf     = document.getElementById('csrfToken')?.value || '';
  const csrfHdr  = document.getElementById('csrfHeader')?.value || 'X-CSRF-TOKEN';

  // Printer — only send if the checkbox is checked and a printer is selected
  const printNow    = document.getElementById('printNow');
  const printerSel  = document.getElementById('printerName');
  const printerName = (printNow?.checked && printerSel?.value)
                      ? printerSel.value : '';

  if (!comboId) {
    alert('Please select a ballot combination first.');
    return;
  }

  btn.disabled    = true;
  btn.textContent = 'Generating…';

  const body = new URLSearchParams();
  body.append('combinationId', comboId);
  body.append('copies',        copies);
  body.append('lang',          lang);
  if (printerName) body.append('printerName', printerName);

  fetch('/print/generate', {
    method:  'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      [csrfHdr]:      csrf,
    },
    body: body.toString(),
  })
  .then(response => {
    if (!response.ok) throw new Error('Server returned ' + response.status);
    const filesHeader = response.headers.get('X-Ballot-Files') || '';
    const files = filesHeader ? filesHeader.split('|') : [];
    return response.blob().then(blob => ({ blob, files }));
  })
  .then(({ blob, files }) => {
    // Open PDF in a new tab for visual confirmation
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');

    if (files.length > 0) {
      const list = document.getElementById('filesDialogList');
      list.innerHTML = '';
      files.forEach(f => {
        const li = document.createElement('li');
        li.textContent = f;
        list.appendChild(li);
      });
      // Add printed-to note if a printer was used
      if (printerName) {
        const li = document.createElement('li');
        li.style.cssText = 'margin-top:.5rem;color:#166534;font-style:italic';
        li.textContent = '🖨 Sent to printer: ' + printerName;
        list.appendChild(li);
      }

      lastGeneratedComboId = document.getElementById('combinationId').value;
      const note = document.getElementById('exportNote');
      if (note) note.style.display = 'none';
      const dlg = document.getElementById('filesDialog');
      dlg.showModal();
      setTimeout(() => {
        const ok = document.getElementById('filesDialogOk');
        if (ok) ok.focus();
      }, 100);
    }
  })
  .catch(err => {
    alert('Error generating ballot: ' + err.message);
  })
  .finally(() => {
    btn.disabled    = false;
    btn.textContent = 'Generate PDF';
  });
}

document.addEventListener('DOMContentLoaded', function () {
  var btn = document.getElementById('generateBtn');
  if (btn) btn.addEventListener('click', generateBallot);

  var ok = document.getElementById('filesDialogOk');
  if (ok) ok.addEventListener('click', closeFilesDialog);

  var dlg = document.getElementById('filesDialog');
  if (dlg) {
    dlg.addEventListener('click', function (e) {
      if (e.target === dlg) closeFilesDialog();
    });
  }
});
