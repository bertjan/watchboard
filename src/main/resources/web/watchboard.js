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
  group.each(function() {
    $(this).outerHeight(tallest);
  });

}

function setGraphsToEqualHeight() {
  setEqualHeight($("#images li"));
}

