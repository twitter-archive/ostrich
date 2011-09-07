
percentiles = [ "25%", "50%", "75%", "90%", "95%", "99%", "99.9%", "99.99%" ];
display_lines = [ false, true, true, false, false, true, false, false ];

$fake = false;
$timing = false;
$params = {};

$(document).ready(function() {
  parseParams();

  if ("g" in $params) {
    drawContent();
  } else {
    $.getJSON("/graph_data", function(datadump) {
      var keys = datadump["keys"].sort();
      for (i in keys) {
        $("#contents").append('<a href="/graph/?g=' + keys[i] + '">' + keys[i] + '</a><br/>');
      }
      $("#graph-container").css("display", "none");
    });
  }
});

function decode(s) {
  return decodeURIComponent(s.replace(/\+/g, " "));
}

function parseParams() {
  var params = window.location.search.substring(1);
  var re = /([^&=]+)=?([^&]*)/g;
  var pair;

  while (pair = re.exec(params)) {
    $params[decode(pair[1])] = decode(pair[2]);
  }
}


function roundTo(number, digits) {
  return Math.round(number * Math.pow(10, digits)) / Math.pow(10, digits);
}

function clickBox(n) {
  display_lines[n] = !display_lines[n];
  $('input[name=box' + n + ']').attr('checked', display_lines[n]);
  getData();
}

function clickLog() {
  if ($params["log"] > 0) {
    $params["log"] = 0;
  } else {
    $params["log"] = 1;
  }
  getData();
}

// turn an [x, y1, y2...] into [[x, y1], [x, y2], ...]
function inflateY(vector) {
  var x = vector.shift() * 1000;
  return vector.map(function(y) { return [x, y]; });
}

function rotate(matrix) {
  var rv = new Array();
  $.each(matrix, function(rindex, row) {
    $.each(row, function(cindex, cell) {
      if (rindex == 0) {
        rv[cindex] = new Array();
      }
      rv[cindex].push(cell);
    });
  });
  return rv;
}

function showTooltip(x, y, contents) {
  $('<div id="tooltip">' + contents + '</div>').css({
    position: 'absolute',
    display: 'none',
    top: y + 5,
    left: x + 5,
    border: '1px solid #fdd',
    padding: '2px',
    'background-color': '#fee',
    opacity: 0.80
  }).appendTo("body").fadeIn(200);
}

function hideTooltip() {
  $('#tooltip').remove();
}

function drawContent() {
  $("#display_lines").append("<input type=checkbox name=log onClick=\"clickLog()\" /> log scale");
  $("input[name=log]").attr("checked", $params["log"] > 0);
  $("#display_lines").append("<p />");

  if ($params["g"].substr(0, 7) == "timing:") {
    $timing = true;
    for (i = 0; i < percentiles.length; i++) {
      $("#display_lines").append("<input type=checkbox name=box" + i + " onClick=\"clickBox(" + i + ")\" /> " + percentiles[i] + "<br />");
      $("input[name=box" + i + "]").attr("checked", display_lines[i]);
    }
    if ($params["g"].substr(7, 4) == "FAKE") {
      $fake = true;
    }
  }
  getData();
}

function getData() {
  if ($fake) {
    var rawData = [
      [ 1283818430, 12, 14, 20, ],
      [ 1283818490, 11, 13, 21, ],
      [ 1283818550, 10, 13, 25, ],
      [ 1283818610, 13, 15, 23, ],
      [ 1283818660, 10, 18, 26, ],
    ];
    drawChart(rawData);
  } else {
    var param = "";
    for (i = 0; i < percentiles.length; i++) {
      if (display_lines[i]) {
        if (param.length > 0) {
          param += ",";
        }
        param += i;
      }
    }
    $.getJSON("/graph_data/" + $params["g"] + "?p=" + param, function(datadump) {
      var rawData = datadump[$params["g"]];
      drawChart(rawData);
    });
  }
}

function drawChart(rawData) {
  var newData = rawData.map(function(row) { return inflateY(row) });
  newData = rotate(newData);
  newData = $.map(newData, function(row) {
    return {
      yaxis: 2,
      data: row
    };
  })
  var options = {
    grid: {
      hoverable: true,
      mouseActiveRadius: 25,
    },
    xaxis: {
      mode: "time"
    },
    y2axis: {
    },
    selection: { mode: "xy" }
  };

  if ($params["log"] > 0) {
    $.extend(options["y2axis"], {
      transform: function (v) { return (v == 0) ? null : Math.log(v); },
      inverseTransform: function (v) { return (v == null) ? 0 : Math.exp(v); }
    });
  }

  var previousPoint = null;

  var chart = $("#chart");
  $.plot(chart, newData, options);

  chart.bind("plothover", function (event, pos, item) {
    if (item && (previousPoint != item.datapoint)) {
      previousPoint = item.datapoint;
      hideTooltip();
      var x = item.datapoint[0].toFixed(2), y = item.datapoint[1].toFixed(2);
      showTooltip(item.pageX, item.pageY, y);
    } else {
      hideTooltip();
    }
  });

  chart.bind("plotselected", function (event, ranges) {
    $("#message").text("Zoomed in -- click anywhere in the graph to return.");
    $.plot(chart, newData, $.extend(true, {}, options, {
      xaxis: { min: ranges.xaxis.from, max: ranges.xaxis.to },
      y2axis: { min: ranges.y2axis.from, max: ranges.y2axis.to },
    }));
  });

  chart.bind("plotunselected", function (event) {
    $("#message").text("");
    $.plot(chart, newData, options);
  });
}
