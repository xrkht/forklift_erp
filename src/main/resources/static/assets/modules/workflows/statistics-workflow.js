export function createStatisticsWorkflow(deps) {
  const {
    dashboardView
  } = deps;

  function renderStatistics() {
    return dashboardView.renderStatistics();
  }

  return {
    renderStatistics
  };
}
