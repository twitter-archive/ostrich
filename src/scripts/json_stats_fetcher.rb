#!/usr/bin/ruby

# json_stats_fetcher.rb - Publish Ostrich stats to Ganglia.
#
# The latest version is always available at:
# http://github.com/twitter/ostrich/blob/master/src/scripts/json_stats_fetcher.rb
#

require 'rubygems'
require 'getoptlong'
require 'socket'
require 'json'
require 'timeout'
require 'open-uri'

$ostrich3 = false # guessed ostrich version
$report_to_ganglia = true
$ganglia_prefix = ''
$stat_timeout = 5*60
$pattern = /^x-/

hostname = "localhost"
port = 9989
period = 60
use_web = false

def usage(port)
  puts
  puts "usage: json_stats_fetcher.rb [options]"
  puts "options:"
  puts "    -n              say what I would report, but don't report it"
  puts "    -w              use web interface"
  puts "    -h <hostname>   connect to another host (default: localhost)"
  puts "    -i <pattern>    ignore all stats matching pattern (default: #{$pattern.inspect})"
  puts "    -p <port>       connect to another port (default: #{port})"
  puts "    -P <prefix>     optional prefix for ganglia names"
  puts "    -t <period>     optional latch period (Ostrich-4.5+)"
  puts
end

opts = GetoptLong.new(
  [ '--help', GetoptLong::NO_ARGUMENT ],
  [ '-n', GetoptLong::NO_ARGUMENT ],
  [ '-h', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-i', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-p', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-P', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-t', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-w', GetoptLong::NO_ARGUMENT ]
  )

opts.each do |opt, arg|
  case opt
  when '--help'
    usage(port)
    exit 0
  when '-n'
    $report_to_ganglia = false
  when '-h'
    hostname = arg
  when '-i'
    $pattern = /#{arg}/
  when '-p'
    port = arg.to_i
  when '-P'
    $ganglia_prefix = arg
  when '-t'
    period = arg.to_i
  when '-w'
    port = 9990
    use_web = true
  end
end

stats_dir = "/tmp/stats-#{port}"
singleton_file = "#{stats_dir}/json_stats_fetcher_running"

Dir.mkdir(stats_dir) rescue nil

if File.exist?(singleton_file)
  puts "NOT RUNNING -- #{singleton_file} exists."
  puts "Kill other stranded stats checker processes and kill this file to resume."
  exit 1
end
File.open(singleton_file, "w") { |f| f.write("i am running.\n") }

## we will accumulate all our metrics in here
metrics = []

begin
  Timeout::timeout(55) do
    data = if use_web
      # Ostrich 4.5+ are latched on a time period
      args = "period=#{period}"
      # Ostrich 3 and 4.0 don't reset counters.
      if $report_to_gangia then
        # Ostrich 2 uses reset
        # Ostrich 4.2 uses namespace for similar functionality
        args += "&reset=1&namespace=ganglia"
      end
      url = "http://#{hostname}:#{port}/stats.json?#{args}"
      open(url).read
    else
      socket = TCPSocket.new(hostname, port)
      socket.puts("stats/json#{' reset' if $report_to_ganglia}")
      socket.gets
    end

    stats = JSON.parse(data)

    # Ostrich >3 puts these in the metrics
    begin
      metrics << ["jvm_threads", stats["jvm"]["thread_count"], "threads"]
      metrics << ["jvm_daemon_threads", stats["jvm"]["thread_daemon_count"], "threads"]
      metrics << ["jvm_heap_used", stats["jvm"]["heap_used"], "bytes"]
      metrics << ["jvm_heap_max", stats["jvm"]["heap_max"], "bytes"]
      metrics << ["jvm_uptime", (stats["jvm"]["uptime"].to_i rescue 0), "items"]
    rescue NoMethodError
      $ostrich3 = true
    end

    begin
      stats["counters"].reject { |name, val| name =~ $pattern }.each do |name, value|
        metrics << [name, (value.to_i rescue 0), "items"]
      end
    rescue NoMethodError
    end

    begin
      stats["gauges"].reject { |name, val| name =~ $pattern }.each do |name, value|
        metrics << [name, value, "value"]
      end
    rescue NoMethodError
    end

    begin
      metricsKey = ($ostrich3) ? "metrics" : "timings"
      stats[metricsKey].reject { |name, val| name =~ $pattern }.each do |name, timing|
        metrics << [name, (timing["average"] || 0).to_f / 1000.0, "sec"]
        metrics << ["#{name}_stddev", (timing["standard_deviation"] || 0).to_f / 1000.0, "sec"]
        [:p25, :p50, :p75, :p90, :p95, :p99, :p999, :p9999].map(&:to_s).each do |bucket|
          metrics << ["#{name}_#{bucket}", (timing[bucket] || 0).to_f / 1000.0, "sec"] if timing[bucket]
        end
      end
    rescue NoMethodError
    end
  end

  ## do stuff with the metrics we've accumulated

  ## first, munge metric names
  metrics = metrics.map do |name, value, units|
    # Ganglia is very intolerant of metric named with non-standard characters,
    # where non-standard contains most everything other than letters, numbers and
    # some common symbols.
    name = name.gsub(/[^A-Za-z0-9_\-\.]/, "_")
    [name, value, units]
  end

  ## now, send to ganglia or print to $stdout
  if $report_to_ganglia # call gmetric for each metric
    cmd = metrics.map do |name, value, units|
      "gmetric -t float -n \"#{$ganglia_prefix}#{name}\" -v \"#{value}\" -u \"#{units}\" -d #{$stat_timeout}"
    end.join("\n")
    puts cmd
    system cmd
  else # print a report to stdout
    report = metrics.map do |name, value, units|
      "#{$ganglia_prefix}#{name}=#{value}"
    end.join("\n")
    puts report
  end
ensure
  File.unlink(singleton_file)
end
