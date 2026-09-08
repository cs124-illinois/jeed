package org.fusesource.jansi

import java.io.PrintStream

/**
 * Stands in for jansi's console streams, which `AnsiConsole.systemInstall()` installs over `System.out` and
 * `System.err`. Only the package matters: the sandbox recognises them by name at startup, since a stream that writes
 * to the file descriptor directly cannot be detected any other way. This one forwards, so tests stay quiet.
 */
class AnsiPrintStreamStub(target: PrintStream) : PrintStream(target, true)
