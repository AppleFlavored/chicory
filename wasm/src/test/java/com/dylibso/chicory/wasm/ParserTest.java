package com.dylibso.chicory.wasm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.dylibso.chicory.log.SystemLogger;
import com.dylibso.chicory.wasm.types.ActiveDataSegment;
import com.dylibso.chicory.wasm.types.CustomSection;
import com.dylibso.chicory.wasm.types.OpCode;
import com.dylibso.chicory.wasm.types.SectionId;
import com.dylibso.chicory.wasm.types.ValueType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ParserTest {

    @Test
    public void shouldParseFile() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/start.wat.wasm")) {
            var module = parser.parseModule(is);

            // check types section
            var typeSection = module.typeSection();
            var types = typeSection.types();
            assertEquals(2, types.length);
            assertEquals("(I32) -> nil", types[0].toString());
            assertEquals("() -> nil", types[1].toString());

            // check import section
            var importSection = module.importSection();
            var imports = importSection.imports();
            assertEquals(1, imports.length);
            assertEquals("func[] <env.gotit>", imports[0].toString());

            // check data section
            var dataSection = module.dataSection();
            var dataSegments = dataSection.dataSegments();
            assertEquals(1, dataSegments.length);
            var segment = (ActiveDataSegment) dataSegments[0];
            assertEquals(0, segment.index());
            assertEquals(OpCode.I32_CONST, segment.offsetInstructions()[0].opcode());
            assertArrayEquals(new byte[] {0x00, 0x01, 0x02, 0x03}, segment.data());

            // check start section
            var startSection = module.startSection();
            assertEquals(1, startSection.startIndex());

            // check function section
            var funcSection = module.functionSection();
            var typeIndices = funcSection.typeIndices();
            assertEquals(1, typeIndices.length);
            assertEquals(1L, (long) typeIndices[0]);

            // check export section
            //        var exportSection = module.getExportSection();
            //        var exports = exportSection.getExports();
            //        assertEquals(1, exports.size());

            // check memory section
            var memorySection = module.memorySection();
            var memories = memorySection.memories();
            assertEquals(1, memories.length);
            assertEquals(1, memories[0].memoryLimits().initialPages());
            assertEquals(65536, memories[0].memoryLimits().maximumPages());

            var codeSection = module.codeSection();
            var functionBodies = codeSection.functionBodies();
            assertEquals(1, functionBodies.length);
            var func = functionBodies[0];
            var locals = func.locals();
            assertEquals(0, locals.size());
            var instructions = func.instructions();
            assertEquals(3, instructions.size());

            assertEquals("0x00000032: I32_CONST [42]", instructions.get(0).toString());
            assertEquals(OpCode.I32_CONST, instructions.get(0).opcode());
            assertEquals(42L, (long) instructions.get(0).operands()[0]);
            assertEquals(OpCode.CALL, instructions.get(1).opcode());
            assertEquals(0L, (long) instructions.get(1).operands()[0]);
            assertEquals(OpCode.END, instructions.get(2).opcode());
        }
    }

    @Test
    public void shouldParseIterfact() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/iterfact.wat.wasm")) {
            var module = parser.parseModule(is);

            // check types section
            var typeSection = module.typeSection();
            var types = typeSection.types();
            assertEquals(1, types.length);
            assertEquals("(I32) -> I32", types[0].toString());

            // check function section
            var funcSection = module.functionSection();
            var typeIndices = funcSection.typeIndices();
            assertEquals(1, typeIndices.length);
            assertEquals(0L, (long) typeIndices[0]);

            var codeSection = module.codeSection();
            var functionBodies = codeSection.functionBodies();
            assertEquals(1, functionBodies.length);
            var func = functionBodies[0];
            var locals = func.locals();
            assertEquals(1, locals.size());
            assertEquals(1, locals.get(0).asInt());
            var instructions = func.instructions();
            assertEquals(22, instructions.size());
        }
    }

    @Test
    public void shouldParseAllFiles() {
        File compiledDir = new File("src/test/resources/compiled/");
        File wasmDir = new File("src/test/resources/wasm/");

        List<File> files = new ArrayList<>();
        files.addAll(
                Arrays.asList(
                        compiledDir.listFiles(
                                (ignored, name) -> name.toLowerCase().endsWith(".wasm"))));
        files.addAll(
                Arrays.asList(
                        wasmDir.listFiles(
                                (ignored, name) -> name.toLowerCase().endsWith(".wasm"))));

        if (files.isEmpty()) {
            throw new RuntimeException("Could not find files");
        }

        var logger = new SystemLogger();
        for (var f : files) {
            var parser = new Parser(logger);
            try (InputStream is = new FileInputStream(f)) {
                parser.parseModule(is);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Failed to parse file %s", f), e);
            }
        }
    }

    @Test
    public void shouldSupportCustomListener() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/wasm/code.wasm")) {
            parser.includeSection(SectionId.CUSTOM);
            parser.parse(
                    is,
                    s -> {
                        if (s.sectionId() == SectionId.CUSTOM) {
                            var customSection = (CustomSection) s;
                            var name = customSection.name();
                            assertFalse(name.isEmpty());
                        } else {
                            fail("Should not have received section with id: " + s.sectionId());
                        }
                    });
        }
    }

    //    @Test
    //    public void shouldParseAst() {
    //        var parser = new Parser("src/test/resources/wasm/code.wasm");
    //        var module = parser.parseModule();
    //        var codeSection = module.getCodeSection();
    //        var fbody = codeSection.getFunctionBodies()[0];
    //        var ast = fbody.getAst();
    //        ast.print();
    //    }

    @Test
    public void shouldParseFloats() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/float.wat.wasm")) {
            var module = parser.parseModule(is);
            var codeSection = module.codeSection();
            var fbody = codeSection.functionBodies()[0];
            var f32 = Float.intBitsToFloat((int) fbody.instructions().get(0).operands()[0]);
            assertEquals(0.12345678f, f32, 0.0);
            var f64 = Double.longBitsToDouble(fbody.instructions().get(1).operands()[0]);
            assertEquals(0.123456789012345d, f64, 0.0);
        }
    }

    @Test
    public void shouldProperlyParseSignedValue() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/i32.wat.wasm")) {
            var module = parser.parseModule(is);
            var codeSection = module.codeSection();
            var fbody = codeSection.functionBodies()[0];
            assertEquals(-2147483648L, fbody.instructions().get(0).operands()[0]);
            assertEquals(0L, fbody.instructions().get(2).operands()[0]);
            assertEquals(2147483647L, fbody.instructions().get(4).operands()[0]);
            assertEquals(-9223372036854775808L, fbody.instructions().get(6).operands()[0]);
            assertEquals(0L, fbody.instructions().get(8).operands()[0]);
            assertEquals(9223372036854775807L, fbody.instructions().get(10).operands()[0]);
            assertEquals(-2147483647L, fbody.instructions().get(12).operands()[0]);
            assertEquals(2147483646L, fbody.instructions().get(14).operands()[0]);
            assertEquals(-9223372036854775807L, fbody.instructions().get(16).operands()[0]);
            assertEquals(9223372036854775806L, fbody.instructions().get(18).operands()[0]);
            assertEquals(-1L, fbody.instructions().get(20).operands()[0]);
            assertEquals(1L, fbody.instructions().get(22).operands()[0]);
            assertEquals(-1L, fbody.instructions().get(24).operands()[0]);
            assertEquals(1L, fbody.instructions().get(26).operands()[0]);
        }
    }

    @Test
    public void shouldParseLocalDefinitions() throws Exception {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/define-locals.wat.wasm")) {
            var module = parser.parseModule(is);
            var codeSection = module.codeSection();
            var fbody = codeSection.functionBodies()[0];
            assertEquals(fbody.locals().get(0).type(), ValueType.I32);
            assertEquals(fbody.locals().get(1).type(), ValueType.I64);
        }
    }

    @Test
    public void shouldParseNamesSection() throws IOException {
        var logger = new SystemLogger();
        var parser = new Parser(logger);

        try (InputStream is = getClass().getResourceAsStream("/compiled/count_vowels.rs.wasm")) {
            var module = parser.parseModule(is);
            var nameSec = module.nameSection();
            assertEquals(125, nameSec.functionNames().size());
        }
    }
}
