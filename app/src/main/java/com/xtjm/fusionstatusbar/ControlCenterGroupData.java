package com.xtjm.fusionstatusbar;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Device-independent positions inside one card, shared by the editor and SystemUI. */
final class ControlCenterGroupData {
    static final int UNITS = 12;
    static final int MAX_MEMBERS = 7;
    enum Arrangement {
        VERTICAL("纵向列表"), HORIZONTAL("横向排列"), GRID("均匀网格"), MIXED("大小混排"), CUSTOM("自由排列");
        final String label;
        Arrangement(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    enum Surface {
        DARK("深色", 0xc8232529), FROSTED("雾面", 0x408c8c92), CLEAR("透明", 0x00000000);
        final String label;
        final int color;
        Surface(String label, int color) { this.label = label; this.color = color; }
        @Override public String toString() { return label; }
    }
    static final class Member {
        final String spec;
        final int x, y, width, height;
        final boolean showLabel;
        Member(String spec, int x, int y, int width, int height, boolean showLabel) {
            this.spec = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
            if (this.spec.isEmpty() || this.spec.length() > 128 || ControlCenterComponentSpec.isSpecial(this.spec)
                    || this.spec.startsWith("group:") || x < 0 || y < 0 || width < 1 || height < 1
                    || width > UNITS || height > UNITS || x > UNITS - width || y > UNITS - height)
                throw new IllegalArgumentException("Invalid group member");
            this.x = x; this.y = y; this.width = width; this.height = height; this.showLabel = showLabel;
        }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Member m)) return false;
            return spec.equals(m.spec) && x == m.x && y == m.y && width == m.width && height == m.height && showLabel == m.showLabel;
        }
        @Override public int hashCode() { return Objects.hash(spec, x, y, width, height, showLabel); }
    }
    final Arrangement arrangement;
    final Surface surface;
    final boolean border;
    final List<Member> members;

    ControlCenterGroupData(Arrangement arrangement, Surface surface, boolean border, List<Member> members) {
        if (arrangement == null || surface == null || members == null || members.size() > MAX_MEMBERS)
            throw new IllegalArgumentException("Invalid group");
        HashSet<String> specs = new HashSet<>();
        boolean[][] occupied = new boolean[UNITS][UNITS];
        for (Member m : members) {
            if (m == null || !specs.add(m.spec)) throw new IllegalArgumentException("Duplicate group member");
            for (int y = m.y; y < m.y + m.height; y++) for (int x = m.x; x < m.x + m.width; x++) {
                if (occupied[y][x]) throw new IllegalArgumentException("Overlapping group members");
                occupied[y][x] = true;
            }
        }
        this.arrangement = arrangement; this.surface = surface; this.border = border;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }
    static ControlCenterGroupData empty() {
        return new ControlCenterGroupData(Arrangement.VERTICAL, Surface.FROSTED, true, Collections.emptyList());
    }
    ControlCenterGroupData withAppearance(Surface surface, boolean border) {
        return new ControlCenterGroupData(arrangement, surface, border, members);
    }
    ControlCenterGroupData withMembers(List<Member> members) {
        return new ControlCenterGroupData(arrangement, surface, border, members);
    }
    ControlCenterGroupData arrange(Arrangement next) {
        if (next == Arrangement.CUSTOM) return new ControlCenterGroupData(next, surface, border, members);
        ArrayList<Member> arranged = new ArrayList<>();
        int count = members.size();
        for (int i = 0; i < count; i++) {
            int x, y, w, h;
            if (next == Arrangement.VERTICAL || next == Arrangement.HORIZONTAL) {
                int start = i * UNITS / count, end = (i + 1) * UNITS / count;
                boolean vertical = next == Arrangement.VERTICAL;
                x = vertical ? 0 : start; y = vertical ? start : 0;
                w = vertical ? UNITS : end - start; h = vertical ? end - start : UNITS;
            } else if (next == Arrangement.MIXED) {
                if (i < 3) { x = i == 1 ? 6 : 0; y = i == 2 ? 6 : 0; w = h = 6; }
                else { x = 6 + (i - 3) % 2 * 3; y = 6 + (i - 3) / 2 * 3; w = h = 3; }
            } else {
                int columns = count <= 1 ? 1 : count <= 4 ? 2 : 3;
                int rows = (count + columns - 1) / columns;
                x = i % columns * UNITS / columns; y = i / columns * UNITS / rows;
                w = UNITS / columns; h = (i / columns + 1) * UNITS / rows - y;
            }
            arranged.add(new Member(members.get(i).spec, x, y, w, h, next == Arrangement.VERTICAL));
        }
        return new ControlCenterGroupData(next, surface, border, arranged);
    }
    ControlCenterGroupData add(String spec) {
        if (members.size() >= MAX_MEMBERS) throw new IllegalArgumentException("Group full");
        ArrayList<Member> next = new ArrayList<>(members);
        if (arrangement == Arrangement.CUSTOM) {
            for (int size : new int[] {3, 2, 1}) for (int y = 0; y <= UNITS - size; y++) {
                for (int x = 0; x <= UNITS - size; x++) {
                    boolean free = true;
                    for (Member member : members) {
                        if (x < member.x + member.width && x + size > member.x
                                && y < member.y + member.height && y + size > member.y) { free = false; break; }
                    }
                    if (free) {
                        next.add(new Member(spec, x, y, size, size, false));
                        return withMembers(next);
                    }
                }
            }
            throw new IllegalArgumentException("No free group slot");
        }
        // Arrange before validating overlap with the temporary placeholder.
        next.add(new Member(spec, 0, 0, 1, 1, false));
        return rearrangeSpecs(next, arrangement);
    }
    ControlCenterGroupData remove(String spec) {
        ArrayList<Member> next = new ArrayList<>(members);
        next.removeIf(m -> m.spec.equals(spec));
        return arrangement == Arrangement.CUSTOM ? withMembers(next) : rearrangeSpecs(next, arrangement);
    }
    ControlCenterGroupData move(int from, int to) {
        if (from < 0 || to < 0 || from >= members.size() || to >= members.size()) return this;
        ArrayList<Member> next = new ArrayList<>(members);
        Collections.swap(next, from, to);
        if (arrangement != Arrangement.CUSTOM) return rearrangeSpecs(next, arrangement);
        Member a = members.get(from), b = members.get(to);
        next.set(from, new Member(b.spec, a.x, a.y, a.width, a.height, b.showLabel));
        next.set(to, new Member(a.spec, b.x, b.y, b.width, b.height, a.showLabel));
        return withMembers(next);
    }
    private ControlCenterGroupData rearrangeSpecs(List<Member> entries, Arrangement kind) {
        ArrayList<Member> temporary = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) temporary.add(new Member(entries.get(i).spec, i, 0, 1, 1, false));
        return new ControlCenterGroupData(kind, surface, border, temporary).arrange(kind);
    }
    ControlCenterGroupData edit(Member changed) {
        ArrayList<Member> next = new ArrayList<>(members);
        for (int i = 0; i < next.size(); i++) if (next.get(i).spec.equals(changed.spec)) next.set(i, changed);
        return new ControlCenterGroupData(Arrangement.CUSTOM, surface, border, next);
    }
    void write(DataOutputStream out) throws IOException {
        out.writeByte(arrangement.ordinal()); out.writeByte(surface.ordinal()); out.writeBoolean(border);
        out.writeByte(members.size());
        for (Member m : members) {
            out.writeUTF(m.spec); out.writeByte(m.x); out.writeByte(m.y);
            out.writeByte(m.width); out.writeByte(m.height); out.writeBoolean(m.showLabel);
        }
    }
    static ControlCenterGroupData read(DataInputStream in) throws IOException {
        int a = in.readUnsignedByte(), s = in.readUnsignedByte(); boolean border = in.readBoolean();
        int count = in.readUnsignedByte();
        if (a >= Arrangement.values().length || s >= Surface.values().length || count > MAX_MEMBERS) throw new IOException("Invalid group data");
        ArrayList<Member> members = new ArrayList<>();
        for (int i = 0; i < count; i++) members.add(new Member(in.readUTF(), in.readUnsignedByte(), in.readUnsignedByte(),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readBoolean()));
        return new ControlCenterGroupData(Arrangement.values()[a], Surface.values()[s], border, members);
    }
    @Override public boolean equals(Object object) {
        return object instanceof ControlCenterGroupData g && arrangement == g.arrangement && surface == g.surface
                && border == g.border && members.equals(g.members);
    }
    @Override public int hashCode() { return Objects.hash(arrangement, surface, border, members); }
}
