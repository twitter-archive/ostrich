#!/usr/bin/ruby

require 'rubygems'
require 'getoptlong'
require 'socket'
require 'json'


def report_metric(name, value, units)
  if $report_to_ganglia
    system("gmetric -t float -n \"#{$ganglia_prefix}#{name}\" -v \"#{value}\" -u \"#{units}\" -d #{$stat_timeout}")
  else
    puts "#{$ganglia_prefix}#{name}=#{value} #{units}"
  end
end

$report_to_ganglia = true
$ganglia_prefix = ''

stat_timeout = 86400
hostname = "localhost"
port = 9989

def usage
  puts
  puts "usage: json_stats_fetcher.rb [options]"
  puts "options:"
  puts "    -n              say what I would report, but don't report it"
  puts "    -h <hostname>   connect to another host (default: localhost)"
  puts "    -p <port>       connect to another port (default: #{port})"
  puts "    -P <prefix>     optional prefix for ganglia names"
  puts
end

opts = GetoptLong.new(
  [ '--help', GetoptLong::NO_ARGUMENT ],
  [ '-n', GetoptLong::NO_ARGUMENT ],
  [ '-h', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-p', GetoptLong::REQUIRED_ARGUMENT ],
  [ '-P', GetoptLong::REQUIRED_ARGUMENT ]
  )

opts.each do |opt, arg|
  case opt
  when '--help'
    usage
    exit 0
  when '-n'
    $report_to_ganglia = false
  when '-h'
    hostname = arg
  when '-p'
    port = arg.to_i
  when '-P'
    $ganglia_prefix = arg
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
  socket = TCPSocket.new(hostname, port)
  socket.puts("stats/json#{' reset' if $report_to_ganglia}")
  stats = JSON.parse(socket.read)

  report_metric("jvm_threads", stats["jvm"]["thread_count"], "threads")
  report_metric("jvm_daemon_threads", stats["jvm"]["thread_daemon_count"], "threads")
  report_metric("jvm_heap_used", stats["jvm"]["heap_used"], "bytes")
  report_metric("jvm_heap_max", stats["jvm"]["heap_max"], "bytes")

  stats["counters"].each do |name, value|
    report_metric(name, (value.to_i rescue 0), "items")
  end

  stats["gauges"].each do |name, value|
    report_metric(name, value, "value")
  end

  stats["timings"].each do |name, timing|
    report_metric(name, (timing["average"] || 0).to_f / 1000.0, "sec")
    report_metric("#{name}_stddev", (timing["standard_deviation"] || 0).to_f / 1000.0, "sec")
  end
ensure
  File.unlink(singleton_file)
end
