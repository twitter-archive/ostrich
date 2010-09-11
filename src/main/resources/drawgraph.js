
percentiles = [ "25%", "50%", "75%", "90%", "95%", "99%", "99.9%", "99.99%" ];
display_lines = [ false, true, true, false, false, true, false, false ];

$fake = false;

$(document).ready(function() {
  if (document.location.search.length > 0) {
    drawContent();
  } else {
    $.getJSON("/graph_data", function(datadump) {
      var keys = datadump["keys"].sort();
      for (i in keys) {
        $("#contents").append('<a href="graph.html?' + keys[i] + '">' + keys[i] + '</a><br/>');
      }
      $("#graph-container").css("display", "none");
    });
  }
});

function roundTo(number, digits) {
  return Math.round(number * Math.pow(10, digits)) / Math.pow(10, digits);
}

function clickBox(n) {
  display_lines[n] = !display_lines[n];
  $('input[name=box' + n + ']').attr('checked', display_lines[n]);
  getData();
}

// turn an [x, y1, y2...] into [[x, y1], [x, y2], ...]
function inflateY(vector) {
  var x = vector.shift();
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
  if (document.location.search.substr(1, 7) == "timing:") {
    for (i = 0; i < percentiles.length; i++) {
      $("#display_lines").append("<input type=checkbox name=box" + i + " onClick=\"clickBox(" + i + ")\" />" + percentiles[i] + "<br />");
      $("input[name=box" + i + "]").attr("checked", display_lines[i]);
    }
  }
  if (document.location.search.substr(8, 4) == "FAKE") {
    $fake = true;
  }
  getData();
}

function getData() {
  if ($fake) {
    var rawData = [
      [ 1283818430000, 12, 14, 20, ],
      [ 1283818490000, 11, 13, 21, ],
      [ 1283818550000, 10, 13, 25, ],
      [ 1283818610000, 13, 15, 23, ],
      [ 1283818660000, 10, 18, 26, ],
    ];
    drawChart(rawData);
  } else {
    var key = document.location.search.substr(1);
    var param = "";
    for (i = 0; i < percentiles.length; i++) {
      if (display_lines[i]) {
        if (param.length > 0) {
          param += ",";
        }
        param += i;
      }
    }
    $.getJSON("/graph_data/" + key + "?p=" + param, function(datadump) {
      var rawData = datadump[key];
      drawChart(rawData);
    });
  }
}

function drawChart(rawData) {
  var newData = rawData.map(function(row) { return inflateY(row) });
  newData = rotate(newData);
  newData = $.map(newData, function(row) {
    return {
      //label: "yeah",
      yaxis: 2,
      data: row
    };
  })
  var options = {
    grid: {
      hoverable: true
    },
    xaxis: {
      mode: "time"
    },
    y2axis: {
      transform: function (v) { return Math.log(v) + 1; },
      inverseTransform: function (v) { return Math.exp(v); }
    },
  };
  $.plot($("#chart"), newData, options);

  var previousPoint = null;
  $("#chart").bind("plothover", function (event, pos, item) {
    if (item && (previousPoint != item.datapoint)) {
      previousPoint = item.datapoint;
      hideTooltip();
      var x = item.datapoint[0].toFixed(2), y = item.datapoint[1].toFixed(2);
      showTooltip(item.pageX, item.pageY, y);
    } else {
      hideTooltip();
    }
  });
}

function drawChartGoogly(rawData) {
  var data = new google.visualization.DataTable();
  data.addColumn('datetime', 'Time');
  for (i = 0; i < rawData[0].length - 1; i++) {
    data.addColumn('number', 'Data' + i);
  }
  data.addRows(rawData.map(function(row) { return [ new Date(row[0] * 1000) ].concat(row.slice(1)); }));

  new Dygraph.GVizChart(document.getElementById('chart')).draw(data, {
    includeZero: true,
    fillGraph: true,
    labelsKMG2: true,
    xAxisLabelFormatter: function(date, granularity) { return date.strftime("%H:%M"); },
    labelsDivStyles: { display: "none" },
    highlightCallback: function(e, x, pts) {
      var xloc = Math.floor(pts[pts.length - 1].canvasx) + 15;
      var yloc = Math.floor(pts[pts.length - 1].canvasy) + 15;
      var label = pts.map(function(p) { return roundTo(p.yval, 3); }).join(", ");
      $('#chart_label').html(label);
      $('#chart_label').css({ display: "block", left: xloc + "px", top: yloc + "px" });
    },
    unhighlightCallback: function(e) {
      $('#chart_label').css({ display: "none" });
    },
  });
}
