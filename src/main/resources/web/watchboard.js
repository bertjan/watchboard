var appVersion;
var configLastUpdated;
var lastAppRefresh = new Date().getTime();
var lastUpdated = 0;
var numberOfColumns;

function setURLHash() {
  location.hash = 'columns=' + numberOfColumns;
}

function setColumns(numCols) {
  numberOfColumns = numCols;
  setURLHash();
  refreshPage();
}

function refreshPage() {
  location.reload(true);
  lastAppRefresh = new Date().getTime();
}

function endsWith(str, suffix) {
  return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function getHashParam() {
  var vars = [], hash;
  var hashes = decodeURIComponent(location.hash).split('|');
  for (var i = 0; i < hashes.length; i++) {
    hash = hashes[i].split('=');
    key = hash[0];
    value = hash[1];

    if (key.charAt(0) === '#' ) {
      key = key.substring(1);
    }
    vars.push(key);
    vars[key] = value;
  }
  return vars;
}

function renderDashboardList() {
  $.ajax({
    url: 'api/v1/dashboards',
    success:function(data) {

      data.dashboards.sort(function(dash1, dash2) {
        return dash1.title.localeCompare(dash2.title);
      });

      $("#dashboards").html("<ul>");
      for (var i = 0; i < data.dashboards.length; i++) {
        dashboard = data.dashboards[i];
        dashboardLink = dashboard.id;
        if (dashboard.defaultNumberOfColumns) {
          dashboardLink += "#columns=" + dashboard.defaultNumberOfColumns;
        }

        $("#dashboards").html($("#dashboards").html() +
          "<li><a href=\""+ dashboardLink + "\">" + escapeMarkup(dashboard.title) + "</a></li>");
      }
      $("#dashboards").html($("#dashboards").html() + "</ul>")
    },
    error:function(jqXHR, textStatus,errorThrown) {
      $("#dashboards").text("Error while fetching dashboards. Check your internet connection.");
    }
  });
}

function escapeMarkup(markup) {
  return $('<div/>').text(dashboard.title).html();
}

function performInitialGraphsRender() {
  $.ajax({
    url: '../api/v1/status/' + dashboardId,
    success: function (data) {
      appVersion = data.appVersion;
      configLastUpdated = data.configLastUpdated;
      $("#title").text(data.title);

      imageHTML = "";
      for (var i = 0; i < data.images.length; i++) {
        image = data.images[i];
        imageHTML +=
          "<a href=\"" + image.url + "\" target=\"_blank\">" +
          "<img id=\"" + image.id + "\" " +
          "data-lastmodified=\"" + image.lastModified + "\" " +
          "src=\"" + image.filename + "\" " +
          "title=\"" + 'Last updated: ' + new Date(image.lastModified) + "\" " +
          ">" +
          "</a>";
      }
      $("#images").html(imageHTML);
      $("#images").attr("style", " -webkit-column-count: " + numberOfColumns + "; -moz-column-count: " + numberOfColumns + "; column-count: " + numberOfColumns + ";");
    }
  });
}


function startGraphUpdateLoop() {
  pathname = window.location.pathname;
  if (endsWith(pathname, '/')) {
    pathname = pathname.substring(0, pathname.length - 1);
  }
  dashboardId = pathname.substring(pathname.lastIndexOf('/') + 1, pathname.length);

  // Read number of columns from URL parameter.
  numberOfColumns = getHashParam()["columns"];
  if (numberOfColumns == undefined) {
    numberOfColumns = 2;
  }

  // Render column selection.
  maxNumberOfColumns = 4;
  var columnSelectionHTML = "Columns: ";
  for (col = 1; col <= maxNumberOfColumns; col++) {
    // Add switch link.
    if (col != numberOfColumns) {
      columnSelectionHTML += "<a href=\"#\" onClick=\"setColumns(" + col + "); event.preventDefault();\">" + col + "</a>";
    } else {
      columnSelectionHTML += col;
    }

    if (col != maxNumberOfColumns) {
      columnSelectionHTML += " | ";
    }
  }
  $("#columnSelection").html(columnSelectionHTML);
  imageWidthPercentage = (100 / numberOfColumns) - 1;

  // Initial rendering.
  performInitialGraphsRender();

  // Periodic update.
  setInterval(function () {
    $.ajax({
      url: '../api/v1/status/' + dashboardId,
      success: function (data) {
        // (New backend version OR config update)
        // AND last refresh was over 30 seconds ago (to throttle refreshes).
        if (((appVersion != data.appVersion) ||  (configLastUpdated != data.configLastUpdated))
          && (new Date().getTime() - lastAppRefresh > 30000)) {
          // Force reload of page from server to keep front- and backend in sync.
          refreshPage();
        }

        for (var i = 0; i < data.images.length; i++) {
          image = data.images[i];
          imageElement = $('img#' + image.id)
          storedLastModified = imageElement.attr("data-lastmodified");
          newLastModified = image.lastModified;
          if (newLastModified != storedLastModified) {
            // refresh.
            imageElement.attr('data-lastmodified', image.lastModified);
            imageElement.attr('src', image.filename + '?' + image.lastModified);
            imageElement.attr('title', 'Last updated: ' + new Date(image.lastModified));
          }
          if (newLastModified > lastUpdated) {
            lastUpdated = newLastModified;
            $("#lastUpdated").text(new Date(lastUpdated));
          }
        }

      }
    });
    // Scan each second for updated images.
  }, 1000);
}

function fetchDashboardConfig() {
  $.ajax({
    url: '../api/v1/config',
    success:function(data) {
      updateConfigTextarea(data);
    },
    error:function(jqXHR, textStatus,errorThrown) {
      $("#message").text("Fetching config failed: " + errorThrown);
    }
  });
}

function saveDashboardConfig() {
  $("#message").text("Saving config...");
  data = {};
  try {
    data.config = JSON.parse($("#config").val());
    data.updatedAt = $("#updatedAt").text();
  } catch (e) {
    $("#message").text(e);
    return;
  }

  $.ajax({
    url: '../api/v1/config',
    method: 'POST',
    data: JSON.stringify(data),
    contentType: "application/json; charset=utf-8",
    dataType: "json",
    success:function(data) {
      updateConfigTextarea(data);
    },
    error:function(jqXHR, textStatus,errorThrown) {
      $("#message").text("Saving config failed: " + errorThrown);
    }
  });
}

function updateConfigTextarea(data) {
  $("#config").val(stringifyDashboardConfig(data.config));
  $("#message").text(data.message);
  $("#updatedAt").text(data.updatedAt);
}

function stringifyDashboardConfig(data) {
  newdata = {};
  newdata.dashboards = [];
  for (d = 0; d < data.dashboards.length; d++) {
    sourceDashboard = data.dashboards[d];
    targetDashboard = {};

    // Copy properties in fixed order.
    copyProperties(sourceDashboard, targetDashboard, ['id', 'title', 'defaultNumberOfColumns']);

    // Copy graphs.
    targetDashboard.graphs = [];
    for (g = 0; g < sourceDashboard.graphs.length; g++) {
      sourceGraph = sourceDashboard.graphs[g];
      targetGraph = {};

      // Copy properties in fixed order, then copy remaining properties.
      copyProperties(sourceGraph, targetGraph, ['id', 'type', 'url', 'components', 'browserWidth', 'browserHeight']);
      copyPropertiesDiff(sourceGraph, targetGraph);
      targetDashboard.graphs.push(targetGraph);
    }

    // Copy remaining properties.
    copyPropertiesDiff(sourceDashboard, targetDashboard);

    newdata.dashboards.push(targetDashboard);
  }

  return JSON.stringify(newdata, null, 2);
}


function removeFromArray(arr, item) {
  for(var i = arr.length; i--;) {
    if(arr[i] === item) {
      arr.splice(i, 1);
    }
  }
}

function copyProperties(source, target, keys) {
  for (i=0; i<keys.length; i++) {
    target[keys[i]] = source[keys[i]];
  }
}

function copyPropertiesDiff(source, target) {
  // Determine non-copied keys.
  otherKeys = Object.keys(source);
  addedKeys = Object.keys(target);
  for (k = 0; k < addedKeys.length; k++) {
    removeFromArray(otherKeys, addedKeys[k]);
  }
  // Copy non-copied properties.
  for (k = 0; k < otherKeys.length; k++) {
    target[otherKeys[k]] = source[otherKeys[k]];
  }
}
