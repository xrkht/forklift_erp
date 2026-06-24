export function createDetailDrawer({
  state,
  getEls,
  transitionMs,
  findEntity,
  renderCurrentTab,
  detailTitle,
  entityLabel,
  detailFields,
  renderDetailGrid,
  renderDetailDrawerActions,
  escapeHtml
}) {
  let closeTimer = null;

  function openDetailDrawer(kind, id) {
    const item = findEntity(kind, Number(id || 0));
    if (!item?.id) return;
    state.detailDrawer = { kind, id: Number(id) };
    renderCurrentTab();
  }

  function closeDetailDrawer() {
    const els = getEls();
    state.detailDrawer = null;
    if (!els?.detailDrawerOverlay) return;
    if (closeTimer) {
      window.clearTimeout(closeTimer);
      closeTimer = null;
    }
    els.detailDrawerOverlay.classList.remove("is-open");
    els.detailDrawerOverlay.setAttribute("aria-hidden", "true");
    const hideDrawer = () => {
      els.detailDrawerOverlay.classList.add("is-hidden");
      els.detailDrawer.innerHTML = "";
      closeTimer = null;
    };
    if (els.detailDrawerOverlay.classList.contains("is-hidden")) {
      hideDrawer();
      return;
    }
    closeTimer = window.setTimeout(hideDrawer, transitionMs);
  }

  function renderDetailDrawer() {
    const els = getEls();
    if (!els?.detailDrawerOverlay) return;
    const drawer = state.detailDrawer;
    if (!drawer) {
      closeDetailDrawer();
      return;
    }
    const item = findEntity(drawer.kind, Number(drawer.id));
    if (!item?.id) {
      closeDetailDrawer();
      return;
    }
    if (closeTimer) {
      window.clearTimeout(closeTimer);
      closeTimer = null;
    }
    const title = detailTitle(drawer.kind, item);
    els.detailDrawer.innerHTML = `
      <div class="detail-drawer-head">
        <div>
          <div class="detail-drawer-kicker">${escapeHtml(entityLabel(drawer.kind))}</div>
          <h2>${escapeHtml(title)}</h2>
        </div>
        <button class="btn btn-ghost btn-sm" type="button" data-action="close-detail">关闭</button>
      </div>
      <div class="detail-drawer-body">
        ${renderDetailGrid(detailFields(drawer.kind, item))}
        ${renderDetailDrawerActions(drawer.kind, item)}
      </div>
    `;
    els.detailDrawerOverlay.classList.remove("is-hidden");
    els.detailDrawerOverlay.setAttribute("aria-hidden", "false");
    requestAnimationFrame(() => {
      els.detailDrawerOverlay.classList.add("is-open");
    });
  }

  return {
    openDetailDrawer,
    closeDetailDrawer,
    renderDetailDrawer
  };
}
