using System;
using System.Diagnostics;
using System.IO;
using System.Linq;

internal static class PacketTracerPtExAppLauncher
{
    private const string MainClass = "packettracer.exapp.PacketTracerPtExApp";

    private static int Main(string[] args)
    {
        string exePath = AppDomain.CurrentDomain.BaseDirectory;
        try
        {
            using (Process current = Process.GetCurrentProcess())
            {
                if (current != null && current.MainModule != null && !string.IsNullOrWhiteSpace(current.MainModule.FileName))
                {
                    exePath = current.MainModule.FileName;
                }
            }
        }
        catch
        {
            // ignored
        }
        string exeDir = Path.GetDirectoryName(exePath) ?? Directory.GetCurrentDirectory();
        string appJar = Path.Combine(exeDir, "pt-exapp.jar");

        if (!File.Exists(appJar))
        {
            return Fail(exeDir, "pt-exapp.jar not found next to launcher executable.");
        }

        string frameworkJar = ResolveFrameworkJar();
        if (string.IsNullOrWhiteSpace(frameworkJar) || !File.Exists(frameworkJar))
        {
            return Fail(exeDir, "Packet Tracer framework JAR not found. Set PACKET_TRACER_JAVA_FRAMEWORK_JAR or install Packet Tracer in a known location.");
        }

        string classPath = string.Join(Path.PathSeparator.ToString(), new[] { appJar, frameworkJar });
        string javaArgs = "-cp \"" + classPath + "\" " + MainClass;

        if (args.Length > 0)
        {
            string forwarded = string.Join(" ", args.Select(QuoteArgument));
            javaArgs = javaArgs + " " + forwarded;
        }

        var startInfo = new ProcessStartInfo
        {
            FileName = ResolveJavaExecutable(),
            Arguments = javaArgs,
            WorkingDirectory = exeDir,
            UseShellExecute = false,
            CreateNoWindow = true,
        };

        try
        {
            using (var process = Process.Start(startInfo))
            {
                if (process == null)
                {
                    return Fail(exeDir, "Failed to start java process.");
                }

                process.WaitForExit();
                WriteLauncherLog(exeDir, "exit=" + process.ExitCode);
                return process.ExitCode;
            }
        }
        catch (Exception exception)
        {
            return Fail(exeDir, "Launcher exception: " + exception.Message);
        }
    }

    private static string ResolveFrameworkJar()
    {
        string configured = Environment.GetEnvironmentVariable("PACKET_TRACER_JAVA_FRAMEWORK_JAR") ?? string.Empty;
        if (!string.IsNullOrWhiteSpace(configured) && File.Exists(configured))
        {
            return configured;
        }

        string[] defaults =
        {
            @"C:\Program Files\Cisco Packet Tracer 8.2.0\help\default\ipc\pt-cep-java-framework-8.1.0.0.jar",
            @"C:\Program Files\Cisco Packet Tracer 8.2.2\help\default\ipc\pt-cep-java-framework-8.2.0.0.jar",
            @"C:\Program Files\Cisco Packet Tracer 9.0\help\default\ipc\pt-cep-java-framework-9.0.0.0.jar",
            @"C:\Program Files\Cisco Packet Tracer\help\default\ipc\pt-cep-java-framework-9.0.0.0.jar",
        };

        foreach (string candidate in defaults)
        {
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return string.Empty;
    }

    private static string ResolveJavaExecutable()
    {
        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME") ?? string.Empty;
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            string fromJavaHome = Path.Combine(javaHome, "bin", "java.exe");
            if (File.Exists(fromJavaHome))
            {
                return fromJavaHome;
            }
        }

        string[] candidates =
        {
            @"C:\Program Files\Java\jdk-17\bin\java.exe",
            @"C:\Program Files\Java\jdk-21\bin\java.exe",
            @"C:\Program Files\Java\jdk-11\bin\java.exe",
        };

        foreach (string candidate in candidates)
        {
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        return "java";
    }

    private static int Fail(string baseDir, string message)
    {
        WriteLauncherLog(baseDir, message);
        return 1;
    }

    private static string QuoteArgument(string value)
    {
        if (value.IndexOf(' ') < 0 && value.IndexOf('"') < 0)
        {
            return value;
        }

        return "\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"";
    }

    private static void WriteLauncherLog(string baseDir, string message)
    {
        try
        {
            string logPath = Path.Combine(baseDir, "pt-exapp-launcher.log");
            File.AppendAllText(logPath, DateTime.UtcNow.ToString("O") + " " + message + Environment.NewLine);
        }
        catch
        {
            // ignored
        }
    }
}
