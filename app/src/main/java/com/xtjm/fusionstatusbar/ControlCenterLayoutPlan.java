package com.xtjm.fusionstatusbar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Versioned, device-independent control-center grid positions. */
final class ControlCenterLayoutPlan {
    /**
     * Version 5 adds a bounded group payload to GROUP items; versions 1-4 remain readable.
     * The CCLP2 prefix is kept because it identifies the plan family;
     * the byte version remains the compatibility boundary.
     */
    static final int VERSION = 5;
    static final int MIN_COLUMNS = 3;
    static final int MAX_COLUMNS = 6;
    static final int MAX_HEIGHT = 6;
    static final int MAX_ROWS = 128;
    static final int UNPLACED = -1;

    private static final String PREFIX = "CCLP2:";
    private static final String LEGACY_PREFIX = "CCLP1:";
    private static final int MAGIC = 0x43434c50;
    private static final int MAX_ITEMS = 256;
    private static final int MAX_SPEC_LENGTH = 128;
    private static final int MAX_BINARY_BYTES = 128 * 1024;
    private static final int MIN_Z_INDEX = Short.MIN_VALUE;
    private static final int MAX_Z_INDEX = Short.MAX_VALUE;
    private static final int STATE_LOCKED = 1;
    private static final int STATE_HIDDEN = 1 << 1;

    enum Type { TILE, PAIR, COMPONENT, GROUP }
    enum Direction { HORIZONTAL, VERTICAL }
    enum Shape { RECTANGLE, CAPSULE, CIRCLE }
    enum RuntimeLayout { AUTOMATIC, REGULAR, COMPACT }

    static final class Item {
        final String id;
        final Type type;
        final String firstSpec;
        final String secondSpec;
        final Direction direction;
        final int x;
        final int y;
        final int width;
        final int height;
        final Shape shape;
        final int cornerRadius;
        /** Editor-only state until the runtime renderer consumes these flags. */
        final boolean locked;
        final boolean hidden;
        final int zIndex;
        final ControlCenterGroupData group;

        private Item(Type type, String firstSpec, String secondSpec, Direction direction,
                int x, int y, int width, int height) {
            this(type, firstSpec, secondSpec, direction, x, y, width, height,
                    Shape.RECTANGLE, 0);
        }

        private Item(Type type, String firstSpec, String secondSpec, Direction direction,
                int x, int y, int width, int height, Shape shape, int cornerRadius) {
            this(type, firstSpec, secondSpec, direction, x, y, width, height, shape,
                    cornerRadius, false, false, 0);
        }

        private Item(Type type, String firstSpec, String secondSpec, Direction direction,
                int x, int y, int width, int height, Shape shape, int cornerRadius,
                boolean locked, boolean hidden, int zIndex) {
            this(type, firstSpec, secondSpec, direction, x, y, width, height, shape, cornerRadius,
                    locked, hidden, zIndex, null);
        }

        private Item(Type type, String firstSpec, String secondSpec, Direction direction,
                int x, int y, int width, int height, Shape shape, int cornerRadius,
                boolean locked, boolean hidden, int zIndex, ControlCenterGroupData group) {
            if (type == null) throw new IllegalArgumentException("Missing item type");
            this.type = type;
            this.firstSpec = spec(firstSpec);
            this.secondSpec = spec(secondSpec);
            this.direction = direction == null ? Direction.HORIZONTAL : direction;
            this.shape = shape == null ? Shape.RECTANGLE : shape;
            this.cornerRadius = Math.max(0, Math.min(64, cornerRadius));
            this.locked = locked;
            this.hidden = hidden;
            this.zIndex = clamp(zIndex, MIN_Z_INDEX, MAX_Z_INDEX);
            this.group = group;
            if ((type == Type.GROUP) != (group != null)) throw new IllegalArgumentException("Missing group data");
            if (type == Type.GROUP && !this.firstSpec.startsWith("group:")) throw new IllegalArgumentException("Invalid group id");
            if (this.firstSpec.isEmpty() || this.firstSpec.length() > MAX_SPEC_LENGTH
                    || this.secondSpec.length() > MAX_SPEC_LENGTH) {
                throw new IllegalArgumentException("Invalid item spec");
            }
            if (type == Type.PAIR) {
                if (this.secondSpec.isEmpty() || this.firstSpec.equals(this.secondSpec)
                        || ControlCenterComponentSpec.isSpecial(this.firstSpec)
                        || ControlCenterComponentSpec.isSpecial(this.secondSpec)) {
                    throw new IllegalArgumentException("A pair needs two distinct tiles");
                }
                String a = this.firstSpec.compareTo(this.secondSpec) <= 0
                        ? this.firstSpec : this.secondSpec;
                String b = a.equals(this.firstSpec) ? this.secondSpec : this.firstSpec;
                id = "pair:" + a.length() + ':' + a + ':' + b;
            } else {
                if (!this.secondSpec.isEmpty()) {
                    throw new IllegalArgumentException("Single item has a second spec");
                }
                id = this.firstSpec;
            }
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        static Item tile(String spec, int x, int y, int width, int height) {
            return new Item(Type.TILE, spec, "", Direction.HORIZONTAL, x, y, width, height);
        }

        static Item pair(String first, String second, Direction direction, int x, int y,
                int width, int height) {
            return new Item(Type.PAIR, first, second, direction, x, y, width, height);
        }

        static Item component(String spec, int x, int y, int width, int height) {
            return new Item(Type.COMPONENT, spec, "", Direction.HORIZONTAL, x, y, width, height);
        }

        static Item group(String id, ControlCenterGroupData data, int x, int y, int width, int height) {
            return new Item(Type.GROUP, id, "", Direction.HORIZONTAL, x, y, width, height,
                    Shape.RECTANGLE, 28, false, false, 0, data);
        }

        Item withGroup(ControlCenterGroupData data) {
            if (type != Type.GROUP) throw new IllegalStateException("Not a group");
            return new Item(type, firstSpec, secondSpec, direction, x, y, width, height,
                    shape, cornerRadius, locked, hidden, zIndex, data);
        }

        List<String> specs() {
            if (type == Type.GROUP) {
                ArrayList<String> result = new ArrayList<>();
                for (ControlCenterGroupData.Member member : group.members) result.add(member.spec);
                return result;
            }
            return type == Type.PAIR ? java.util.Arrays.asList(firstSpec, secondSpec) : Collections.singletonList(firstSpec);
        }

        Item withPosition(int nextX, int nextY) {
            return new Item(type, firstSpec, secondSpec, direction, nextX, nextY, width, height,
                    shape, cornerRadius, locked, hidden, zIndex, group);
        }

        Item withSize(int nextWidth, int nextHeight) {
            return new Item(type, firstSpec, secondSpec, direction, x, y, nextWidth, nextHeight,
                    shape, cornerRadius, locked, hidden, zIndex, group);
        }

        Item withDirection(Direction nextDirection) {
            return new Item(type, firstSpec, secondSpec, nextDirection, x, y, width, height,
                    shape, cornerRadius, locked, hidden, zIndex, group);
        }

        Item withShape(Shape nextShape, int nextCornerRadius) {
            return new Item(type, firstSpec, secondSpec, direction, x, y, width, height,
                    nextShape, nextCornerRadius, locked, hidden, zIndex, group);
        }

        Item withState(boolean nextLocked, boolean nextHidden, int nextZIndex) {
            return new Item(type, firstSpec, secondSpec, direction, x, y, width, height,
                    shape, cornerRadius, nextLocked, nextHidden, nextZIndex, group);
        }

        Item withLocked(boolean nextLocked) {
            return withState(nextLocked, hidden, zIndex);
        }

        Item withHidden(boolean nextHidden) {
            return withState(locked, nextHidden, zIndex);
        }

        Item withZIndex(int nextZIndex) {
            return withState(locked, hidden, nextZIndex);
        }

        boolean containsSpec(String target) {
            String normalized = spec(target);
            return specs().contains(normalized);
        }
    }

    static final class Issue {
        final String code;
        final String itemId;

        Issue(String code, String itemId) {
            this.code = code;
            this.itemId = itemId;
        }
    }

    static final class Mode {
        final int columns;
        final List<Item> items;

        Mode(int columns, List<Item> items) {
            this.columns = columns;
            this.items = Collections.unmodifiableList(new ArrayList<>(
                    items == null ? Collections.emptyList() : items));
        }

        Mode withItems(List<Item> nextItems) {
            return new Mode(columns, nextItems);
        }

        Mode withColumns(int nextColumns) {
            return new Mode(nextColumns, items);
        }

        List<Issue> validate() {
            ArrayList<Issue> issues = new ArrayList<>();
            if (columns < MIN_COLUMNS || columns > MAX_COLUMNS) {
                issues.add(new Issue("COLUMNS", ""));
                return issues;
            }
            if (items.size() > MAX_ITEMS) {
                issues.add(new Issue("TOO_MANY_ITEMS", ""));
            }
            Set<String> ids = new HashSet<>();
            Set<String> members = new HashSet<>();
            boolean[][] occupied = new boolean[MAX_ROWS][columns];
            for (Item item : items) {
                if (item == null) {
                    issues.add(new Issue("NULL_ITEM", ""));
                    continue;
                }
                if (!ids.add(item.id)) issues.add(new Issue("DUPLICATE_ID", item.id));
                for (String member : item.specs()) if (!members.add(member)) issues.add(new Issue("DUPLICATE_MEMBER", item.id));
                if (item.width < 1 || item.width > columns || item.height < 1
                        || item.height > MAX_HEIGHT) {
                    issues.add(new Issue("SIZE", item.id));
                    continue;
                }
                if (item.type == Type.PAIR && ((item.direction == Direction.HORIZONTAL
                        && item.width < 2) || (item.direction == Direction.VERTICAL
                        && item.height < 2))) {
                    issues.add(new Issue("PAIR_SIZE", item.id));
                }
                if (item.hidden) continue;
                if (item.x == UNPLACED && item.y == UNPLACED) continue;
                if (item.x < 0 || item.y < 0 || item.x > columns - item.width
                        || item.y > MAX_ROWS - item.height) {
                    issues.add(new Issue("BOUNDS", item.id));
                    continue;
                }
                if (!free(occupied, item.x, item.y, item.width, item.height)) {
                    issues.add(new Issue("COLLISION", item.id));
                } else {
                    occupy(occupied, item.x, item.y, item.width, item.height);
                }
            }
            return issues;
        }
    }

    static final class Resolved {
        final Mode mode;
        final List<Item> items;
        final List<String> movedIds;
        final List<String> unavailableSpecs;

        private Resolved(Mode mode, List<String> movedIds, List<String> unavailableSpecs) {
            this.mode = mode;
            this.items = mode.items;
            this.movedIds = Collections.unmodifiableList(new ArrayList<>(movedIds));
            this.unavailableSpecs = Collections.unmodifiableList(
                    new ArrayList<>(unavailableSpecs));
        }
    }

    final Mode regular;
    final Mode compact;
    final RuntimeLayout runtimeLayout;

    private ControlCenterLayoutPlan(Mode regular, Mode compact) {
        this(regular, compact, RuntimeLayout.AUTOMATIC);
    }

    private ControlCenterLayoutPlan(Mode regular, Mode compact, RuntimeLayout runtimeLayout) {
        if (regular == null || compact == null) {
            throw new IllegalArgumentException("Both layouts are required");
        }
        this.regular = regular;
        this.compact = compact;
        this.runtimeLayout = runtimeLayout;
    }

    static ControlCenterLayoutPlan of(Mode regular, Mode compact) {
        return new ControlCenterLayoutPlan(regular, compact);
    }

    Mode mode(boolean isCompact) {
        return isCompact ? compact : regular;
    }

    ControlCenterLayoutPlan withMode(boolean isCompact, Mode nextMode) {
        return new ControlCenterLayoutPlan(isCompact ? regular : nextMode,
                isCompact ? nextMode : compact, runtimeLayout);
    }

    ControlCenterLayoutPlan forPublication(boolean isCompact) {
        return new ControlCenterLayoutPlan(regular, compact,
                isCompact ? RuntimeLayout.COMPACT : RuntimeLayout.REGULAR);
    }

    boolean runtimeUsesCompact(boolean nativeCompact) {
        return runtimeLayout == RuntimeLayout.AUTOMATIC ? nativeCompact
                : runtimeLayout == RuntimeLayout.COMPACT;
    }

    Mode runtimeMode(boolean nativeCompact) {
        return mode(runtimeUsesCompact(nativeCompact));
    }

    Resolved resolveForRuntime(boolean nativeCompact, List<String> availableSpecs) {
        return resolveVisible(runtimeUsesCompact(nativeCompact), availableSpecs);
    }

    ControlCenterLayoutPlan withItem(boolean isCompact, Item nextItem) {
        Mode current = mode(isCompact);
        ArrayList<Item> items = new ArrayList<>();
        boolean replaced = false;
        for (Item item : current.items) {
            if (item.id.equals(nextItem.id)) {
                if (!replaced) items.add(nextItem);
                replaced = true;
            } else if (!Collections.disjoint(nextItem.specs(), item.specs())) {
                if (item.type == Type.GROUP) {
                    ArrayList<ControlCenterGroupData.Member> members = new ArrayList<>(item.group.members);
                    members.removeIf(m -> nextItem.containsSpec(m.spec));
                    items.add(item.withGroup(item.group.withMembers(members)));
                } else if (item.type == Type.PAIR) {
                    if (!nextItem.containsSpec(item.firstSpec)) {
                        items.add(Item.tile(item.firstSpec, UNPLACED, UNPLACED, 1, 1)
                                .withState(item.locked, item.hidden, item.zIndex));
                    }
                    if (!nextItem.containsSpec(item.secondSpec)) {
                        items.add(Item.tile(item.secondSpec, UNPLACED, UNPLACED, 1, 1)
                                .withState(item.locked, item.hidden, item.zIndex));
                    }
                }
            } else {
                items.add(item);
            }
        }
        if (!replaced) items.add(nextItem);
        return withMode(isCompact, current.withItems(items));
    }

    ControlCenterLayoutPlan removeItem(boolean isCompact, String itemId) {
        Mode current = mode(isCompact);
        ArrayList<Item> items = new ArrayList<>(current.items);
        items.removeIf(item -> item.id.equals(itemId));
        return withMode(isCompact, current.withItems(items));
    }

    ControlCenterLayoutPlan withPair(boolean isCompact, String first, String second,
            Direction direction, int x, int y, int width, int height) {
        return withItem(isCompact, Item.pair(first, second, direction, x, y, width, height));
    }

    ControlCenterLayoutPlan unpair(boolean isCompact, String pairId) {
        Mode current = mode(isCompact);
        ArrayList<Item> items = new ArrayList<>();
        for (Item item : current.items) {
            if (!item.id.equals(pairId) || (item.type != Type.PAIR && item.type != Type.GROUP)) {
                items.add(item);
                continue;
            }
            if (item.type == Type.GROUP) {
                for (String member : item.specs()) items.add(Item.tile(member, UNPLACED, UNPLACED, 1, 1)
                        .withShape(Shape.CIRCLE, 0).withState(item.locked, item.hidden, item.zIndex));
                continue;
            }
            items.add(Item.tile(item.firstSpec, item.x, item.y, 1, 1)
                    .withState(item.locked, item.hidden, item.zIndex));
            int nextX = item.direction == Direction.HORIZONTAL ? item.x + 1 : item.x;
            int nextY = item.direction == Direction.VERTICAL ? item.y + 1 : item.y;
            items.add(Item.tile(item.secondSpec, nextX, nextY, 1, 1)
                    .withState(item.locked, item.hidden, item.zIndex));
        }
        return withMode(isCompact, current.withItems(items));
    }

    Resolved resolve(boolean isCompact, List<String> availableSpecs) {
        Mode source = mode(isCompact);
        int columns = clamp(source.columns, MIN_COLUMNS, MAX_COLUMNS);
        LinkedHashSet<String> available = new LinkedHashSet<>();
        if (availableSpecs != null) {
            for (String value : availableSpecs) {
                String normalized = spec(value);
                if (!normalized.isEmpty() && normalized.length() <= MAX_SPEC_LENGTH) {
                    available.add(normalized);
                }
            }
        }

        ArrayList<Item> candidates = new ArrayList<>();
        LinkedHashSet<String> claimed = new LinkedHashSet<>();
        ArrayList<String> unavailable = new ArrayList<>();
        for (Item item : source.items) {
            if (item == null || candidates.size() >= MAX_ITEMS) continue;
            if (item.type == Type.GROUP) {
                ArrayList<ControlCenterGroupData.Member> members = new ArrayList<>();
                for (ControlCenterGroupData.Member member : item.group.members) {
                    if (availableSpecs != null && !available.contains(member.spec)) unavailable.add(member.spec);
                    else if (claimed.add(member.spec)) members.add(member);
                }
                candidates.add(item.withGroup(item.group.withMembers(members)));
                continue;
            }
            boolean firstPresent = availableSpecs == null || available.contains(item.firstSpec);
            boolean firstFree = firstPresent && !claimed.contains(item.firstSpec);
            if (item.type == Type.PAIR) {
                boolean secondPresent = availableSpecs == null || available.contains(item.secondSpec);
                boolean secondFree = secondPresent && !claimed.contains(item.secondSpec);
                if (firstFree && secondFree) {
                    candidates.add(item);
                    claimed.add(item.firstSpec);
                    claimed.add(item.secondSpec);
                } else if (firstFree || secondFree) {
                    String survivor = firstFree ? item.firstSpec : item.secondSpec;
                    candidates.add(Item.tile(survivor, item.x, item.y, 1, 1)
                            .withState(item.locked, item.hidden, item.zIndex));
                    claimed.add(survivor);
                }
                if (!firstPresent) unavailable.add(item.firstSpec);
                if (!secondPresent) unavailable.add(item.secondSpec);
            } else if (firstFree) {
                candidates.add(item);
                claimed.add(item.firstSpec);
            } else if (!firstPresent) {
                unavailable.add(item.firstSpec);
            }
        }
        boolean[][] occupied = new boolean[MAX_ROWS][columns];
        Item[] placed = new Item[candidates.size()];
        ArrayList<Integer> pending = new ArrayList<>();
        ArrayList<String> moved = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Item item = fit(candidates.get(i), columns);
            candidates.set(i, item);
            if (inside(item, columns) && free(occupied, item.x, item.y,
                    item.width, item.height)) {
                occupy(occupied, item.x, item.y, item.width, item.height);
                placed[i] = item;
            } else {
                pending.add(i);
            }
        }
        for (int index : pending) {
            Item item = candidates.get(index);
            int[] slot = firstFree(occupied, columns, item.width, item.height);
            if (slot == null) continue;
            occupy(occupied, slot[0], slot[1], item.width, item.height);
            placed[index] = item.withPosition(slot[0], slot[1]);
            if (slot[0] != item.x || slot[1] != item.y) moved.add(item.id);
        }
        ArrayList<Item> result = new ArrayList<>();
        for (Item item : placed) if (item != null) result.add(item);
        result.sort(Comparator.comparingInt((Item item) -> item.y).thenComparingInt(item -> item.x));
        return new Resolved(new Mode(columns, result), moved, unavailable);
    }

    /** Resolves only items that should be rendered by SystemUI. Hidden items remain in the
     * editable plan and are intentionally excluded before collision resolution so their old
     * cells become available to visible items. */
    Resolved resolveVisible(boolean isCompact, List<String> availableSpecs) {
        Mode source = mode(isCompact);
        ArrayList<Item> visible = new ArrayList<>();
        for (Item item : source.items) {
            if (item != null && !item.hidden) visible.add(item);
        }
        ControlCenterLayoutPlan filtered = isCompact
                ? of(regular, new Mode(source.columns, visible))
                : of(new Mode(source.columns, visible), compact);
        return filtered.resolve(isCompact, availableSpecs);
    }

    static ControlCenterLayoutPlan fromConfig(ControlCenterConfig config,
            List<String> availableSpecs) {
        ControlCenterLayoutPlan stored = decode(config.layoutPlan);
        if (stored != null) return stored;
        if (!config.hasLegacyLayoutOverride()) {
            return blank(config.columns);
        }
        ArrayList<String> available = new ArrayList<>();
        if (availableSpecs != null) {
            for (String value : availableSpecs) {
                String normalized = spec(value);
                if (!normalized.isEmpty() && !config.isHidden(normalized)
                        && !available.contains(normalized)) available.add(normalized);
            }
        } else {
            available.addAll(config.orderedSpecs());
        }
        ArrayList<String> ordered = new ArrayList<>();
        for (String value : config.orderedSpecs()) {
            if (available.contains(value) && !ordered.contains(value)) ordered.add(value);
        }
        for (String value : config.componentOrder.split(",")) {
            String normalized = spec(value);
            if (available.contains(normalized) && !ordered.contains(normalized)) {
                ordered.add(normalized);
            }
        }
        for (String value : available) if (!ordered.contains(value)) ordered.add(value);
        Mode regular = legacyMode(config, ordered, config.columns);
        Mode compact = legacyMode(config, ordered, MIN_COLUMNS);
        ControlCenterLayoutPlan draft = of(regular, compact);
        return of(draft.resolve(false, null).mode, draft.resolve(true, null).mode);
    }

    /** Builds a new plan from an explicitly captured native order, ignoring any saved plan. */
    static ControlCenterLayoutPlan fromCaptured(ControlCenterConfig config,
            List<String> capturedSpecs) {
        ArrayList<String> ordered = new ArrayList<>();
        if (capturedSpecs != null) {
            for (String value : capturedSpecs) {
                String normalized = spec(value);
                if (!normalized.isEmpty() && !ordered.contains(normalized)) {
                    ordered.add(normalized);
                }
            }
        }
        Mode regular = legacyMode(config, ordered, config.columns);
        Mode compact = legacyMode(config, ordered, MIN_COLUMNS);
        ControlCenterLayoutPlan captured = of(regular, compact);
        return of(captured.resolve(false, null).mode, captured.resolve(true, null).mode);
    }

    static ControlCenterLayoutPlan blank(int columns) {
        int safe = clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        return of(new Mode(safe, Collections.emptyList()),
                new Mode(Math.max(MIN_COLUMNS, Math.min(safe, 4)), Collections.emptyList()));
    }

    private static Mode legacyMode(ControlCenterConfig config, List<String> ordered, int columns) {
        ArrayList<Item> items = new ArrayList<>();
        for (String value : ordered) {
            boolean component = ControlCenterComponentSpec.isSpecial(value);
            int width = ControlCenterTileLayout.hasShape(config.layout, value)
                    ? config.tileWidth(value) : component
                            ? ControlCenterComponentSpec.defaultSpan(value) : 1;
            int height = ControlCenterTileLayout.hasShape(config.layout, value)
                    ? config.tileHeight(value) : component
                            ? ControlCenterComponentSpec.defaultRows(value) : 1;
            items.add(component
                    ? Item.component(value, UNPLACED, UNPLACED,
                            Math.min(columns, width), height)
                    : Item.tile(value, UNPLACED, UNPLACED,
                            Math.min(columns, width), height));
        }
        return new Mode(columns, items);
    }

    String encode() {
        ControlCenterLayoutPlan clean = of(resolve(false, null).mode, resolve(true, null).mode);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(buffer);
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            writeMode(output, clean.regular);
            writeMode(output, clean.compact);
            output.writeByte(runtimeLayout.ordinal());
            output.flush();
            if (buffer.size() > MAX_BINARY_BYTES) {
                throw new IllegalArgumentException("Layout too large");
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(buffer.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    static ControlCenterLayoutPlan decode(String value) {
        if (value == null || (!value.startsWith(PREFIX) && !value.startsWith(LEGACY_PREFIX))
                || value.length() > value.substring(0, 6).length()
                + (MAX_BINARY_BYTES * 4 / 3) + 4) {
            return null;
        }
        try {
            int prefixLength = value.startsWith(PREFIX) ? PREFIX.length() : LEGACY_PREFIX.length();
            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(prefixLength));
            if (bytes.length > MAX_BINARY_BYTES) return null;
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC) return null;
            int version = input.readUnsignedByte();
            if (version < 1 || version > VERSION) return null;
            Mode regular = readMode(input, version);
            Mode compact = readMode(input, version);
            int selection = version >= 4 ? input.readUnsignedByte() : 0;
            if (selection >= RuntimeLayout.values().length) return null;
            if (input.available() != 0) return null;
            return new ControlCenterLayoutPlan(regular, compact,
                    RuntimeLayout.values()[selection]);
        } catch (IOException | IllegalArgumentException error) {
            return null;
        }
    }

    static String normalize(String value) {
        ControlCenterLayoutPlan plan = decode(value);
        return plan == null ? "" : plan.encode();
    }

    private static void writeMode(DataOutputStream output, Mode mode) throws IOException {
        output.writeByte(mode.columns);
        output.writeShort(mode.items.size());
        for (Item item : mode.items) {
            output.writeByte(item.type.ordinal());
            output.writeByte(item.direction.ordinal());
            output.writeByte(item.shape.ordinal());
            output.writeByte(item.cornerRadius);
            int state = (item.locked ? STATE_LOCKED : 0)
                    | (item.hidden ? STATE_HIDDEN : 0);
            output.writeByte(state);
            output.writeShort(item.zIndex);
            output.writeShort(item.x);
            output.writeShort(item.y);
            output.writeByte(item.width);
            output.writeByte(item.height);
            output.writeUTF(item.firstSpec);
            output.writeUTF(item.secondSpec);
            if (item.type == Type.GROUP) item.group.write(output);
        }
    }

    private static Mode readMode(DataInputStream input, int version)
            throws IOException {
        boolean styled = version >= 2, stateful = version >= 3;
        int columns = input.readUnsignedByte();
        int count = input.readUnsignedShort();
        if (columns < MIN_COLUMNS || columns > MAX_COLUMNS || count > MAX_ITEMS) {
            throw new IOException("Invalid layout size");
        }
        ArrayList<Item> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int typeCode = input.readUnsignedByte();
            int directionCode = input.readUnsignedByte();
            if (typeCode >= (version >= 5 ? Type.values().length : 3) || directionCode >= Direction.values().length) {
                throw new IOException("Invalid item kind");
            }
            int shapeCode = styled ? input.readUnsignedByte() : Shape.RECTANGLE.ordinal();
            int cornerRadius = styled ? input.readUnsignedByte() : 0;
            if (shapeCode >= Shape.values().length) throw new IOException("Invalid item shape");
            int stateFlags = stateful ? input.readUnsignedByte() : 0;
            if ((stateFlags & ~(STATE_LOCKED | STATE_HIDDEN)) != 0) {
                throw new IOException("Invalid item state");
            }
            int zIndex = stateful ? input.readShort() : 0;
            int x = input.readShort();
            int y = input.readShort();
            int width = input.readUnsignedByte();
            int height = input.readUnsignedByte();
            String first = input.readUTF();
            String second = input.readUTF();
            ControlCenterGroupData group = typeCode == Type.GROUP.ordinal() ? ControlCenterGroupData.read(input) : null;
            items.add(new Item(Type.values()[typeCode], first, second,
                    Direction.values()[directionCode], x, y, width, height,
                    Shape.values()[shapeCode], cornerRadius,
                    (stateFlags & STATE_LOCKED) != 0,
                    (stateFlags & STATE_HIDDEN) != 0,
                    zIndex, group));
        }
        Mode mode = new Mode(columns, items);
        if (!mode.validate().isEmpty()) throw new IOException("Invalid item positions");
        return mode;
    }

    private static Item fit(Item item, int columns) {
        int width = clamp(item.width, 1, columns);
        int height = clamp(item.height, 1, MAX_HEIGHT);
        if (item.type == Type.PAIR) {
            if (item.direction == Direction.HORIZONTAL) width = Math.max(2, width);
            else height = Math.max(2, height);
        }
        return item.withSize(width, height);
    }

    private static boolean inside(Item item, int columns) {
        return item.x >= 0 && item.y >= 0 && item.x <= columns - item.width
                && item.y <= MAX_ROWS - item.height;
    }

    private static int[] firstFree(boolean[][] occupied, int columns, int width, int height) {
        for (int y = 0; y <= MAX_ROWS - height; y++) {
            for (int x = 0; x <= columns - width; x++) {
                if (free(occupied, x, y, width, height)) return new int[] {x, y};
            }
        }
        return null;
    }

    private static boolean free(boolean[][] occupied, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                if (occupied[row][col]) return false;
            }
        }
        return true;
    }

    private static void occupy(boolean[][] occupied, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) occupied[row][col] = true;
        }
    }

    private static String spec(String value) {
        return ControlCenterConfig.canonicalSpec(value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
