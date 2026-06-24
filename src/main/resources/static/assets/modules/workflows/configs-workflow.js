export function createConfigWorkflow(deps) {
  const {
    state,
    filterRows,
    renderToolbar,
    renderSurface,
    renderTable,
    renderSelectableAttrs,
    rowActions,
    hasPermission,
    badge,
    emptyState,
    escapeAttr,
    escapeHtml,
    icon
  } = deps;

  function renderConfigs() {
    const items = filterRows(state.data.configItems, state.search.configItems, [
      "category", "subCategory", "itemName", "itemCode", "inputType"
    ]);
    const values = filterRows(state.data.configValues, state.search.configValues, [
      "valueLabel", "valueCode", "remark"
    ]);
    const selectedItem = state.data.configItems.find(item => item.id === state.selectedConfigItemId);
  
    return `
      <div class="page">
        ${renderToolbar(null, "", null, "", null, {
          searches: [
            { key: "configItems", placeholder: "搜索配置项" },
            { key: "configValues", placeholder: "搜索配置值" }
          ],
          exportType: false,
          actions: [
            hasPermission("config:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="configItem">${icon("plus")}新增配置项</button>` : "",
            hasPermission("config:write") ? `<button class="btn" type="button" data-action="create" data-kind="configValue">${icon("plus")}新增配置值</button>` : ""
          ]
        })}
  
        <section class="config-layout">
          <div class="config-pane config-items-pane">
            ${renderSurface("配置项", `
              <div class="config-list">
                ${items.length ? items.map(renderConfigItemCard).join("") : emptyState("暂无配置项")}
              </div>
            `)}
          </div>
          <div class="config-pane config-values-pane">
            ${renderSurface(selectedItem ? `配置值 · ${escapeHtml(selectedItem.itemName || "")}` : "配置值", `
              ${selectedItem ? `<div class="helper">当前配置项：${escapeHtml(selectedItem.category || "-")} / ${escapeHtml(selectedItem.subCategory || "-")}</div>` : ""}
              ${renderTable([
                { label: "显示值", key: "valueLabel" },
                { label: "编码", key: "valueCode" },
                { label: "默认", html: true, render: row => row.isDefault ? badge("默认", "teal") : badge("普通", "primary") },
                { label: "排序", key: "sortOrder" },
                { label: "操作", html: true, render: row => rowActions("configValue", row, ["delete"]) }
              ], values)}
            `)}
          </div>
        </section>
      </div>
    `;
  }
  
  function renderConfigsV2() {
    return `
      <div class="page">
        ${renderConfigModuleTabs()}
        ${state.activeConfigModule === "vehicles" ? renderVehicleConfigModule() : renderPartConfigModule()}
      </div>
    `;
  }
  
  function renderConfigModuleTabs() {
    const active = state.activeConfigModule === "vehicles" ? "vehicles" : "parts";
    return `
      <div class="toolbar">
        <div class="status-toggle-group">
          <button class="status-toggle ${active === "parts" ? "teal" : "primary"}" type="button" data-action="set-config-module" data-module="parts" aria-pressed="${active === "parts" ? "true" : "false"}">配件配置</button>
          <button class="status-toggle ${active === "vehicles" ? "teal" : "primary"}" type="button" data-action="set-config-module" data-module="vehicles" aria-pressed="${active === "vehicles" ? "true" : "false"}">整车配置</button>
        </div>
      </div>
    `;
  }
  
  function renderPartConfigModule() {
    const items = filterRows(state.data.configItems, state.search.configItems, [
      "category", "subCategory", "itemName", "itemCode", "inputType"
    ]);
    const values = filterRows(state.data.configValues, state.search.configValues, [
      "valueLabel", "valueCode", "remark"
    ]);
    const selectedItem = state.data.configItems.find(item => item.id === state.selectedConfigItemId);
  
    return `
      ${renderToolbar(null, "", null, "", null, {
        searches: [
          { key: "configItems", placeholder: "搜索配件配置项" },
          { key: "configValues", placeholder: "搜索配件配置值" }
        ],
        exportType: false,
        actions: [
          hasPermission("config:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="configItem">${icon("plus")}新增配置项</button>` : "",
          hasPermission("config:write") ? `<button class="btn" type="button" data-action="create" data-kind="configValue">${icon("plus")}新增配置值</button>` : ""
        ]
      })}
      <section class="config-layout">
        <div class="config-pane config-items-pane">
          ${renderSurface("配件配置项", `
            <div class="config-list">
              ${items.length ? items.map(renderConfigItemCard).join("") : emptyState("暂无配件配置项")}
            </div>
          `)}
        </div>
        <div class="config-pane config-values-pane">
          ${renderSurface(selectedItem ? `配件配置值 · ${escapeHtml(selectedItem.itemName || "")}` : "配件配置值", `
            ${selectedItem ? `<div class="helper">当前配置项：${escapeHtml(selectedItem.category || "-")} / ${escapeHtml(selectedItem.subCategory || "-")}</div>` : ""}
            ${renderTable([
              { label: "显示值", key: "valueLabel" },
              { label: "编码", key: "valueCode" },
              { label: "默认", html: true, render: row => row.isDefault ? badge("默认", "teal") : badge("普通", "primary") },
              { label: "排序", key: "sortOrder" },
              { label: "操作", html: true, render: row => rowActions("configValue", row, ["delete"]) }
            ], values)}
          `)}
        </div>
      </section>
    `;
  }
  
  function renderVehicleConfigModule() {
    const items = filterRows(state.data.vehicleConfigItems, state.search.vehicleConfigItems, [
      "specificationModel", "remark"
    ]);
    const values = filterRows(state.data.vehicleConfigValues, state.search.vehicleConfigValues, [
      "configItemName", "configItemLabel", "configValueLabel", "configValueCode", "remark"
    ]);
    const selectedItem = state.data.vehicleConfigItems.find(item => item.id === state.selectedVehicleConfigItemId);
  
    return `
      ${renderToolbar(null, "", null, "", null, {
        searches: [
          { key: "vehicleConfigItems", placeholder: "搜索规格型号" },
          { key: "vehicleConfigValues", placeholder: "搜索整车配置值" }
        ],
        exportType: false,
        actions: [
          hasPermission("config:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="vehicleConfigItem">${icon("plus")}新增规格型号</button>` : "",
          hasPermission("config:write") ? `<button class="btn" type="button" data-action="create" data-kind="vehicleConfigValue">${icon("plus")}新增整车配置值</button>` : ""
        ]
      })}
      <section class="config-layout">
        <div class="config-pane config-items-pane vehicle-config-items-pane">
          ${renderSurface("整车配置项", `
            <div class="config-list">
              ${items.length ? items.map(renderVehicleConfigItemCard).join("") : emptyState("暂无整车规格型号")}
            </div>
          `)}
        </div>
        <div class="config-pane config-values-pane vehicle-config-values-pane">
          ${renderSurface(selectedItem ? `整车配置值 · ${escapeHtml(selectedItem.specificationModel || "")}` : "整车配置值", `
            ${selectedItem ? `<div class="helper">当前规格型号：${escapeHtml(selectedItem.specificationModel || "-")}</div>` : ""}
            ${renderTable([
              { label: "车辆部位配置项", html: true, render: row => escapeHtml(row.configItemLabel || row.configItemName || "-") },
              { label: "默认配置值", key: "configValueLabel" },
              { label: "编码", key: "configValueCode" },
              { label: "排序", key: "sortOrder" },
              { label: "操作", html: true, render: row => rowActions("vehicleConfigValue", row, ["edit", "delete"]) }
            ], values)}
          `)}
        </div>
      </section>
    `;
  }
  
  function renderConfigItemCard(item) {
    const active = item.id === state.selectedConfigItemId;
    return `
      <article class="config-item is-selectable${active ? " is-active" : ""}"${renderSelectableAttrs({
        action: "select-config-item",
        data: { id: item.id },
        active,
        label: `查看配置项 ${item.itemName || item.itemCode || item.id} 的值列表`
      })}>
        <div class="config-item-head">
          <div>
            <div class="config-item-title">${escapeHtml(item.itemName || "-")}</div>
            <div class="helper">${escapeHtml(item.category || "-")} / ${escapeHtml(item.subCategory || "-")} · ${escapeHtml(item.itemCode || "-")}</div>
          </div>
          ${item.isRequired ? badge("必填", "teal") : badge("可选", "primary")}
        </div>
        <div class="toolbar-actions">
          <button class="btn btn-sm" type="button" data-action="select-config-item" data-id="${escapeAttr(item.id)}">${icon("eye")}值列表</button>
          ${hasPermission("config:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="configItem" data-id="${escapeAttr(item.id)}">${icon("edit")}编辑</button>` : ""}
          ${hasPermission("config:write") ? `<button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="configItem" data-id="${escapeAttr(item.id)}">${icon("trash")}删除</button>` : ""}
        </div>
      </article>
    `;
  }
  
  function renderVehicleConfigItemCard(item) {
    const active = item.id === state.selectedVehicleConfigItemId;
    return `
      <article class="config-item is-selectable${active ? " is-active" : ""}"${renderSelectableAttrs({
        action: "select-vehicle-config-item",
        data: { id: item.id },
        active,
        label: `查看规格型号 ${item.specificationModel || item.id} 的默认配置`
      })}>
        <div class="config-item-head">
          <div>
            <div class="config-item-title">${escapeHtml(item.specificationModel || "-")}</div>
            <div class="helper">${escapeHtml(item.remark || "整车默认配置模板")}</div>
          </div>
          ${badge("规格型号", "teal")}
        </div>
        <div class="toolbar-actions">
          <button class="btn btn-sm" type="button" data-action="select-vehicle-config-item" data-id="${escapeAttr(item.id)}">${icon("eye")}值列表</button>
          ${hasPermission("config:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="vehicleConfigItem" data-id="${escapeAttr(item.id)}">${icon("edit")}编辑</button>` : ""}
          ${hasPermission("config:write") ? `<button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="vehicleConfigItem" data-id="${escapeAttr(item.id)}">${icon("trash")}删除</button>` : ""}
        </div>
      </article>
    `;
  }

  return {
    renderConfigs,
    renderConfigsV2
  };
}
