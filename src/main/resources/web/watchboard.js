var appVersion;
var configLastUpdated;
var lastAppRefresh = new Date().getTime();
var lastUpdated = 0;
var numberOfColumns;

function setURLHash() {
  var imageOrder = $('#imageList').children('li').map(function(){
    return $(this).find("img").attr('id')
  }).get();
  location.hash = 'columns=' + numberOfColumns + "|" + "imageOrder=" + imageOrder;
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
  var hashes = decodeURIComponent(location.hash).split('|'); //window.location.href.split('#')[0].slice(window.location.href.indexOf('?') + 1).split('&');
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

function setEqualHeight(group) {
  // Determine tallest item in group.
  var tallest = 0;
  group.each(function() {
    var thisHeight = $(this).outerHeight();
    if(thisHeight > tallest) {
      tallest = thisHeight;
    }
  });

  // Set height of all items in group to tallest item.
  if (tallest > 0) {
    group.each(function () {
      if ($(this).outerHeight() != tallest) {
        console.log('setting height from ' + $(this).outerHeight() + ' to ' + tallest);
        $(this).outerHeight(tallest);
      }

    });
  }

}

function setGraphsToEqualHeight() {
  setEqualHeight($("#images li"));
}


function renderDashboardList() {
  $.ajax({
    url: 'api/v1/dashboards',
    success:function(data) {

      data.dashboards.sort(function(dash1, dash2) {
        return dash1.id.localeCompare(dash2.id);
      });

      $("#dashboards").html("<ul>");
      for (var i = 0; i < data.dashboards.length; i++) {
        dashboard = data.dashboards[i];
        dashboardLink = dashboard.id;
        if (dashboard.defaultNumberOfColumns) {
          dashboardLink += "#columns=" + dashboard.defaultNumberOfColumns;
        }

        $("#dashboards").html($("#dashboards").html() +
          "<li><a href=\""+ dashboardLink + "\">" + dashboard.title + "</a></li>");
      }
      $("#dashboards").html($("#dashboards").html() + "</ul>")
    },
    error:function(jqXHR, textStatus,errorThrown) {
      $("#dashboards").text("No dashboards found.");
    }
  });
}


function performInitialGraphsRender() {
  $.ajax({
    url: '../api/v1/status/' + dashboardId,
    success: function (data) {
      appVersion = data.appVersion;
      configLastUpdated = data.configLastUpdated;
      $("#title").text(data.title);

      // Sort images based on order in url hash.
      var imageOrder = getHashParam()["imageOrder"];
      if(imageOrder) {
        data.images.sort(function (a, b) {
          return imageOrder.indexOf(a.id) < imageOrder.indexOf(b.id) ? -1 : 1;
        });
      }

      $("#images").html("");
      for (var i = 0; i < data.images.length; i++) {
        image = data.images[i];
        $("#images").html($("#images").html() +
          "<li class=\"ui-state-default\" style=\"width: " + imageWidthPercentage + "%\"><a href=\"" + image.url + "\" target=\"_blank\">" +
          "<img style=\"width: 100%\" id=\"" + image.id + "\" " +
          "data-lastmodified=\"" + image.lastModified + "\" " +
          "src=\"" + image.filename + "\" " +
          "title=\"" + 'Last updated: ' + new Date(image.lastModified) + "\" " +
          ">" +
          "</a></li>");
      }
      $("#images").html("<ul id=\"imageList\">" + $("#images").html() + "</ul>");

      $("#images ul").sortable({
        deactivate: function( event, ui ) {
          setURLHash();
        },
        opacity: 0.7
      });

      window.setTimeout(function() {
        setGraphsToEqualHeight();
      }, 100);

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

        // Make sure all images have the same height.
        setGraphsToEqualHeight();
      }
    });
    // Scan each second for updated images.
  }, 1000);

  // Refresh page after resize to trigger re-render.
  $(window).bind("debouncedresize", function() {
    performInitialGraphsRender();
  });

}