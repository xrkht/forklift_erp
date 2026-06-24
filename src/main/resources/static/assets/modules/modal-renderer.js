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
