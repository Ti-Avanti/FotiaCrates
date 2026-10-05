package gg.fotia.crates.lang;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 清理旧版内置语言文件的重复键，保留 YAML 原本最后生效的值和其他自定义内容。 */
final class LanguageDuplicateMigration {
    private static final Set<String> KEYS = Set.of("admin-crate-saved", "admin-crate-deleted",
            "admin-crate-created", "admin-input-chance");

    private LanguageDuplicateMigration() {}

    static boolean apply(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        Node root = new Yaml().compose(new java.io.StringReader(text));
        if (!(root instanceof MappingNode mapping)) return false;
        MappingNode messages = null;
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key && "messages".equals(key.getValue())
                    && tuple.getValueNode() instanceof MappingNode values) messages = values;
        }
        if (messages == null) return false;
        Map<String, NodeTuple> previous = new HashMap<>();
        List<int[]> removals = new ArrayList<>();
        for (NodeTuple tuple : messages.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key) || !KEYS.contains(key.getValue())) continue;
            NodeTuple earlier = previous.put(key.getValue(), tuple);
            if (earlier == null) continue;
            int start = earlier.getKeyNode().getStartMark().getLine();
            var end = earlier.getValueNode().getEndMark();
            int stop = Math.max(start + 1, end.getLine() + (end.getColumn() == 0 ? 0 : 1));
            removals.add(new int[] {start, stop});
        }
        if (removals.isEmpty()) return false;
        List<String> lines = new ArrayList<>(text.lines().toList());
        removals.sort((left, right) -> Integer.compare(right[0], left[0]));
        for (int[] range : removals) lines.subList(range[0], range[1]).clear();
        String updated = String.join("\n", lines) + "\n";
        // 删除重复项后再次检查语法和别名，避免破坏使用 YAML 锚点的自定义文件。
        new Yaml().compose(new java.io.StringReader(updated));
        Path backup = file.resolveSibling(file.getFileName() + ".duplicate-keys.bak");
        if (!Files.exists(backup)) Files.copy(file, backup);
        Path temporary = Files.createTempFile(file.getParent(), "language-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }
}
