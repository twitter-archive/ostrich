
percentiles = [ "25%", "50%", "75%", "90%", "95%", "99%", "99.9%", "99.99%" ];
display_lines = [ false, true, true, false, false, true, false, false ];

FAKE = false;

if (document.location.search.length > 0) {
  google.load('visualization', '1');
  google.setOnLoadCallback(drawContent);
} else {
  $.getJSON("/graph_data", function(datadump) {
    var keys = datadump["keys"].sort();
    for (i in keys) {
      $("#contents").append('<a href="graph.html?' + keys[i] + '">' + keys[i] + '</a><br/>');
    }
    $("#graph-container").css("display", "none");
  });
}

function roundTo(number, digits) {
  return Math.round(number * Math.pow(10, digits)) / Math.pow(10, digits);
}

function clickBox(n) {
  display_lines[n] = !display_lines[n];
  $('input[name=box' + n + ']').attr('checked', display_lines[n]);
  getData();
}

function drawContent() {
  if (document.location.search.substr(1, 7) == "timing:") {
    for (i = 0; i < percentiles.length; i++) {
      $("#display_lines").append("<input type=checkbox name=box" + i + " onClick=\"clickBox(" + i + ")\" />" + percentiles[i] + "<br />");
      $("input[name=box" + i + "]").attr("checked", display_lines[i]);
    }
  }
  if (document.location.search.substr(8, 4) == "FAKE") {
    FAKE = true;
  }
  getData();
}

function getData() {
  if (FAKE) {
    var rawData = [
      [ 900, 12, 14, 20, ],
      [ 960, 11, 13, 21, ],
      [ 1020, 10, 13, 25, ],
      [ 1080, 13, 15, 23, ],
      [ 1140, 14, 18, 26, ],
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
