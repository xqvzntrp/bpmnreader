package bpmn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

final class CliTests {
    private static int checks;
    private CliTests() { }
    private record Run(int status, String diagnostic) { }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static Run run(String... args) throws Exception {
        Path project = Path.of("").toAbsolutePath(); // Maven and build.sh run from project root
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(project.resolve("target/classes").toString());
        command.add("bpmn.BpmnToMermaid");
        command.addAll(Arrays.asList(args));
        var process = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start();
        String diagnostic = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(process.waitFor(), diagnostic);
    }
    static int run() throws Exception {
        Path dir = Files.createTempDirectory("bpmn-cli-");
        Path input = dir.resolve("input.bpmn");
        Path output = dir.resolve("output.mmd");
        Path htmlOutput = dir.resolve("output.html");
        try {
            Path example = Path.of("examples/order-approval.bpmn");
            byte[] source = Files.readAllBytes(example);
            Run success = run("examples/order-approval.bpmn", output.toString());
            check(success.status() == 0 && success.diagnostic().isEmpty(), "CLI works from repository root");
            check(Files.readString(output, StandardCharsets.UTF_8).equals(new MermaidConverter().convert(example).text()), "UTF-8 CLI output");
            check(Arrays.equals(source, Files.readAllBytes(example)), "example source unchanged");
            check(Files.readString(htmlOutput).equals(MermaidHtml.export(Files.readString(output))), "matching HTML generated automatically");
            for (String[] args : new String[][]{{}, {"one"}, {"one", "two", "three"}}) {
                Run invalid = run(args);
                check(invalid.status() != 0 && invalid.diagnostic().contains("Usage:"), "invalid argument count");
            }
            for (String xml : new String[]{"<broken>", ReaderTests.xml("<subProcess id='s'/>"),
                    ReaderTests.xml("<task id='t'/><sequenceFlow id='f' sourceRef='t' targetRef='absent'/>"),
                    "<!DOCTYPE definitions SYSTEM 'http://127.0.0.1:9/evil.dtd'>" + ReaderTests.xml("")}) {
                Files.writeString(input, xml);
                Files.writeString(output, "keep existing output é 😀");
                Files.writeString(htmlOutput, "keep existing HTML");
                Run failed = run(input.toString(), output.toString());
                check(failed.status() != 0 && failed.diagnostic().startsWith("BPMN conversion failed:")
                    && !failed.diagnostic().contains("Exception in thread"), "concise conversion diagnostic");
                check(Files.readString(output).equals("keep existing output é 😀"), "conversion failure preserves existing output");
                check(Files.readString(input).equals(xml), "invalid source unchanged");
                check(Files.readString(htmlOutput).equals("keep existing HTML"), "conversion failure preserves HTML");
            }
            Files.writeString(input, ReaderTests.xml("<task id='t' name='é 😀'/>"));
            check(run(input.toString(), output.toString()).status() == 0
                && Files.readString(output).contains("#233; #128512;"), "Unicode file conversion");
            Files.delete(htmlOutput);
            Files.createSymbolicLink(htmlOutput, input);
            Files.writeString(output, "keep Mermaid");
            check(run(input.toString(), output.toString()).diagnostic().contains("different files")
                && Files.readString(output).equals("keep Mermaid"), "HTML alias of source rejected before writes");
            Files.delete(htmlOutput);
            Files.createLink(htmlOutput, output);
            check(run(input.toString(), output.toString()).diagnostic().contains("different files")
                && Files.readString(output).equals("keep Mermaid"), "aliased outputs rejected before writes");
            Files.delete(htmlOutput);
            byte[] before = Files.readAllBytes(input);
            Run same = run(input.toString(), input.toString());
            check(same.status() != 0 && same.diagnostic().contains("different files"), "same file rejected");
            check(run(input.toString(), dir.resolve(".").resolve("input.bpmn").toString()).status() != 0, "normalized same path rejected");
            Path symlink = dir.resolve("symlink.bpmn");
            Files.createSymbolicLink(symlink, input);
            check(run(input.toString(), symlink.toString()).diagnostic().contains("different files"), "symlink alias rejected");
            Path hardlink = dir.resolve("hardlink.bpmn");
            Files.createLink(hardlink, input);
            check(run(input.toString(), hardlink.toString()).diagnostic().contains("different files"), "hard link alias rejected");
            check(Arrays.equals(before, Files.readAllBytes(input)), "same-file failures preserve source");
            Files.writeString(output, "keep");
            check(run(dir.resolve("missing.bpmn").toString(), output.toString()).status() != 0
                && Files.readString(output).equals("keep"), "missing input preserves output");
            check(run(dir.toString(), output.toString()).status() != 0, "input directory rejected");
            check(run(input.toString(), dir.resolve("missing/out.mmd").toString()).status() != 0, "missing output directory diagnostic");
            check(run(input.toString(), dir.toString()).status() != 0, "output directory rejected");
            Files.writeString(input, "invalid");
            Path absent = dir.resolve("absent.mmd");
            check(run(input.toString(), absent.toString()).status() != 0 && !Files.exists(absent)
                && !Files.exists(dir.resolve("absent.html")), "failure does not create outputs");
        } finally {
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(dir);
        }
        return checks;
    }
}
