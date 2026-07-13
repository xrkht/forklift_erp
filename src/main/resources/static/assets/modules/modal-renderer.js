export function createModalRenderer({
  state,
  getEls,
  resetModalPointerDown,
  modalTitle,
  modalSubtitle,
  getFields,
  renderModalFields,
  usesVehicleInboundConfigEditor,
  renderConfigSelectionEditor,
  canSaveAndContinue,
  escapeAttr,
  escapeHtml
}) {
  function closeModal() {
    const els = getEls();
    state.modal = null;
    resetModalPointerDown();
    els.modalOverlay.classList.add("is-hidden");
    els.modalOverlay.setAttribute("aria-hidden", "true");
    els.modalCard.innerHTML = "";
  }

  function renderModal() {
    const els = getEls();
    if (!state.modal) return;
    const { kind, item } = state.modal;
    const title = modalTitle(kind, item);
    const modalFields = getFields(kind, item);
    els.modalCard.innerHTML = `
      <div class="modal-head">
        <div>
          <h2 class="surface-title">${escapeHtml(title)}</h2>
          <div class="helper">${escapeHtml(modalSubtitle(kind))}</div>
        </div>
        <button class="btn btn-ghost btn-sm" type="button" data-close-modal>关闭</button>
      </div>
      <form data-kind="${escapeAttr(kind)}">
        <div class="modal-body">
          <div class="modal-grid">
            ${renderModalFields(modalFields, item)}
          </div>
          ${usesVehicleInboundConfigEditor(kind, item) ? renderConfigSelectionEditor(item) : ""}
        </div>
        <div class="modal-actions">
          <button class="btn btn-ghost" type="button" data-close-modal>取消</button>
          ${canSaveAndContinue(kind, item) ? `<button class="btn" type="submit" data-submit-mode="continue">保存并继续</button>` : ""}
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>
    `;
    els.modalOverlay.classList.remove("is-hidden");
    els.modalOverlay.setAttribute("aria-hidden", "false");
  }

  return {
    closeModal,
    renderModal
  };
}

export function setModalSubmitting(form, submitting) {
  if (!form) return;
  if (submitting) {
    form.dataset.submitting = "true";
    form.setAttribute("aria-busy", "true");
    const card = form.closest(".modal-card");
    const controls = [...(card || form).querySelectorAll("button, input, select, textarea")];
    form.__submitControls = controls;
    controls.forEach(control => {
      control.__disabledBeforeSubmit = control.disabled;
      control.disabled = true;
      if (control.matches('button[type="submit"]')) {
        control.__contentBeforeSubmit = control.innerHTML;
        control.textContent = "提交中...";
      }
    });
    return;
  }

  form.dataset.submitting = "false";
  form.removeAttribute("aria-busy");
  (form.__submitControls || []).forEach(control => {
    control.disabled = Boolean(control.__disabledBeforeSubmit);
    if (control.__contentBeforeSubmit !== undefined) {
      control.innerHTML = control.__contentBeforeSubmit;
      delete control.__contentBeforeSubmit;
    }
    delete control.__disabledBeforeSubmit;
  });
  delete form.__submitControls;
}
