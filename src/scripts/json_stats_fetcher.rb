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

def report_metric(name, value, units)
  # Ganglia is very intolerant of metric named with non-standard characters,
  # where non-standard contains most everything other than letters, numbers and
  # some common symbols.  We will substitute underscores for invalid characters.
  if name.length == 0
    $stderr.puts "Metric was empty."
    return 
  end
  name = name.gsub(/[^A-Za-z0-9_\-\.]/, "_")

  if $report_to_ganglia
    system("gmetric -t float -n \"#{$ganglia_prefix}#{name}\" -v \"#{value}\" -u \"#{units}\" -d #{$stat_timeout}")
  else
    puts "#{$ganglia_prefix}#{name}=#{value} #{units}"
  end
end

$ostrich3 = false
$report_to_ganglia = true
$ganglia_prefix = ''
$stat_timeout = 86400
$pattern = /^x-/

hostname = "localhost"
port = 9989
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
  puts
end

opts = GetoptLong.new(
  [ '--help', GetoptLong::NO_ARGUMENT ],
  [ '-n', GetoptLong::NO_ARGUMENT ],
  [ '-h', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-i', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-p', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-P', GetoptLong::REQUIRED_ARGUMENT ],
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

begin
  Timeout::timeout(60) do
    data = if use_web
      open("http://#{hostname}:#{port}/stats.json#{'?reset=1' if $report_to_ganglia}").read
    else
      socket = TCPSocket.new(hostname, port)
      socket.puts("stats/json#{' reset' if $report_to_ganglia}")
      socket.gets
    end

    stats = JSON.parse(data)

    # Ostrich >3 puts these in the metrics
    if !$ostrich3
      report_metric("jvm_threads", stats["jvm"]["thread_count"], "threads")
      report_metric("jvm_daemon_threads", stats["jvm"]["thread_daemon_count"], "threads")
      report_metric("jvm_heap_used", stats["jvm"]["heap_used"], "bytes")
      report_metric("jvm_heap_max", stats["jvm"]["heap_max"], "bytes")
      report_metric("jvm_uptime", (stats["jvm"]["uptime"].to_i rescue 0), "items")
    end

    stats["counters"].reject { |name, val| name =~ $pattern }.each do |name, value|
      report_metric(name, (value.to_i rescue 0), "items")
    end

    stats["gauges"].reject { |name, val| name =~ $pattern }.each do |name, value|
      report_metric(name, value, "value")
    end

    metricsKey = ($ostrich3) ? "metrics" : "timings"
    stats[metricsKey].reject { |name, val| name =~ $pattern }.each do |name, timing|
      report_metric(name, (timing["average"] || 0).to_f / 1000.0, "sec")
      report_metric("#{name}_stddev", (timing["standard_deviation"] || 0).to_f / 1000.0, "sec")
      [:p25, :p50, :p75, :p90, :p99, :p999, :p9999].map(&:to_s).each do |bucket|
        report_metric("#{name}_#{bucket}", (timing[bucket] || 0).to_f / 1000.0, "sec") if timing[bucket]
      end
    end
  end
ensure
  File.unlink(singleton_file)
end
