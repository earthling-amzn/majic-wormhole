#!/usr/bin/env bash

ITERATIONS=10
TARGET_DIR=/dev/shm/receiver-test

{
  echo -n "Transfer Time, Transfer Bytes, Transfer Files, Iteration, Description, Command, Wall, Kernel, User, Max RSS (kb),"
  echo -n "Major Page Faults, Minor Page Faults, Involuntary Context Switches, Voluntary Context Switches,"
  echo    "File System Inputs, File System Outputs"
} > timings.csv

time_it()
{
  # %C      Name and command line arguments of the command being timed.
  # %D      Average size of the process's unshared data area, in Kilobytes.
  # %E      Elapsed real (wall clock) time used by the process, in [hours:]minutes:seconds.
  # %F      Number of major, or I/O-requiring, page faults that occurred while the process was running.  These are faults where the page has actually migrated out of primary memory.
  # %I      Number of file system inputs by the process.
  # %K      Average total (data+stack+text) memory use of the process, in Kilobytes.
  # %M      Maximum resident set size of the process during its lifetime, in Kilobytes.
  # %O      Number of file system outputs by the process.
  # %P      Percentage of the CPU that this job got.  This is just user + system times divided by the total running time.  It also prints a percentage sign.
  # %R      Number of minor, or recoverable, page faults.  These are pages that are not valid (so they fault) but which have not yet been claimed by other virtual pages.  Thus the data in the page is still valid but the system tables must be updated.
  # %S      Total number of CPU-seconds used by the system on behalf of the process (in kernel mode), in seconds.
  # %U      Total number of CPU-seconds that the process used directly (in user mode), in seconds.
  # %W      Number of times the process was swapped out of main memory.
  # %X      Average amount of shared text in the process, in Kilobytes.
  # %Z      System's page size, in bytes.  This is a per-system constant, but varies between systems.
  # %c      Number of times the process was context-switched involuntarily (because the time slice expired).
  # %e      Elapsed real (wall clock) time used by the process, in seconds.
  # %k      Number of signals delivered to the process.
  # %p      Average unshared stack size of the process, in Kilobytes.
  # %r      Number of socket messages received by the process.
  # %s      Number of socket messages sent by the process.
  # %t      Average resident set size of the process, in Kilobytes.
  # %w      Number of times that the program was context-switched voluntarily, for instance while waiting for an I/O operation to complete.
  # %x      Exit status of the command.
  local description=$1
  shift 1
  for i in $(seq 1 $ITERATIONS)
  do
    echo "Starting $i of ${ITERATIONS}"
    /usr/bin/time --format ",$i,$description,\"%C\",%e,%S,%U,%M,%F,%R,%c,%w,%I,%O" --append --output timings.csv "$@" &> "/tmp/${description}.$i.log"
  done
}

start_registry()
{
  echo -n "Starting registry..."
  start_it "$@"
}

start_receiver()
{
  echo -n "Starting receiver..."
  start_it "$@"
}

start_it()
{
    "$@" &>> /tmp/process.out &
    pid=$!
    echo "OK ($pid)"
    sleep 2
}

stop_all()
{
  echo "Stopping"
  jobs
  kill -9 %%
  sleep 2
  kill -9 %%
  sleep 2
  echo "Stopped"
  jobs
}

time_hotspot()
{
  echo "Timing Hotspot"
  export JAVA_HOME=$HOME/Development/genshen/build/linux-x86_64-server-release/jdk
  java=$JAVA_HOME/bin/java

  start_registry $java -XX:+UseSerialGC -XX:MetaspaceSize=512m -Xlog:safepoint=info,gc*=info:/tmp/registry.log -jar wormhole-0.1.jar
  start_receiver $java -XX:+UseSerialGC -XX:MetaspaceSize=512m -Xlog:safepoint=info,gc*=info:/tmp/receiver.log -jar wormhole-0.1.jar recv --username recv --target-dir $TARGET_DIR --accept --forever --direct

  sender="$java -XX:+UseSerialGC -XX:MetaspaceSize=512m -Xms512m -Xmx512m -XX:NativeMemoryTracking=detail -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics -XX:-UsePerfData -XX:-TieredCompilation -Xlog:safepoint=info -jar wormhole-0.1.jar send --receiver recv --sender me --stats timings.csv"
#  time_it $sender --file "$1" --direct --validate --threads 8
  time_it "hotspot-nio" $sender --file "$1" --direct --threads 8 --chunk $((1024 * 1024 * 1))
#  time_it $sender --file "$1" --validate --threads 8
  time_it "hotspot" $sender --file "$1" --threads 8 --chunk $((1024 * 1024 * 100))

  stop_all
}

time_graal()
{
  echo "Timing Graal Native Image"
  wormhole=$HOME/Development/wormhole/wormhole

  start_registry $wormhole
  start_receiver $wormhole recv --username recv --target-dir $TARGET_DIR --accept --forever --direct

  sender="$wormhole send --receiver recv --sender me --stats timings.csv"

#  time_it $sender --file "$1" --direct --validate --threads 8
  time_it "graal-nio" $sender --file "$1" --direct --threads 8
#  time_it $sender --file "$1" --validate --threads 8
  time_it "graal" $sender --file "$1" --threads 8

  stop_all
}

time_rust()
{
  description=$1
  wormhole=$2
  source=$3
  buffer_size=$((100 * 1024 * 1024))

  start_registry $wormhole registry
  start_receiver $wormhole receive recv 9000 $TARGET_DIR

  time_it $description $wormhole send sender recv $source

  stop_all
}

time_rust_async()
{
  echo "Timing Rust (Async)"
  source=$1
  wormhole=$HOME/Development/rusticwormhole/rusticwormhole-async
  time_rust "rust-async" $wormhole $source
}

time_rust_blocking()
{
  echo "Timing Rust (Blocking)"
  source=$1
  wormhole=$HOME/Development/rusticwormhole/target/release/rusticwormhole
  time_rust "rust-blocking" $wormhole $source
}

if [ ! -d "$1" ]
then
  time_rust_async "$1"
fi

time_graal "$1"
time_rust_blocking "$1"

time_hotspot "$1"
