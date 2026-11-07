package teafabric.web.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

/** {@link ModMetadata} backed by the JSON the baker wrote for each mod. */
final class WebModMetadata implements ModMetadata {
    private final String id;
    private final String type;
    private final String name;
    private final String description;
    private final WebVersion version;
    private final ModEnvironment environment;
    private final List<String> provides;
    private final List<String> license;
    private final List<Person> authors = new ArrayList<>();
    private final List<Person> contributors = new ArrayList<>();
    private final Contact contact;
    private final String icon;
    private final Map<String, CustomValue> custom = new LinkedHashMap<>();
    final String parent;

    WebModMetadata(JsonObject o) {
        id = o.get("id").getAsString();
        type = WebFabricLoader.str(o, "type");
        name = WebFabricLoader.str(o, "name");
        description = WebFabricLoader.str(o, "description");
        version = new WebVersion(o.get("version").getAsString());
        environment = ModEnvironment.valueOf(o.get("environment").getAsString());
        provides = WebFabricLoader.strings(o, "provides");
        license = WebFabricLoader.strings(o, "license");
        for (String a : WebFabricLoader.strings(o, "authors")) {
            authors.add(new SimplePerson(a));
        }
        for (String a : WebFabricLoader.strings(o, "contributors")) {
            contributors.add(new SimplePerson(a));
        }
        Map<String, String> c = new LinkedHashMap<>();
        if (o.has("contact") && o.get("contact").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("contact").entrySet()) {
                c.put(e.getKey(), e.getValue().getAsString());
            }
        }
        contact = new Contact(c);
        icon = WebFabricLoader.str(o, "icon");
        parent = WebFabricLoader.str(o, "parent");
        if (o.has("custom") && o.get("custom").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("custom").entrySet()) {
                custom.put(e.getKey(), toCustom(e.getValue()));
            }
        }
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Collection<String> getProvides() {
        return provides;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return name == null ? id : name;
    }

    @Override
    public String getDescription() {
        return description == null ? "" : description;
    }

    @Override
    public Collection<Person> getAuthors() {
        return authors;
    }

    @Override
    public Collection<Person> getContributors() {
        return contributors;
    }

    @Override
    public ContactInformation getContact() {
        return contact;
    }

    @Override
    public Collection<String> getLicense() {
        return license;
    }

    @Override
    public Optional<String> getIconPath(int size) {
        return Optional.ofNullable(icon);
    }

    @Override
    public boolean containsCustomValue(String key) {
        return custom.containsKey(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return custom.get(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return Collections.unmodifiableMap(custom);
    }

    @Override
    @Deprecated
    public boolean containsCustomElement(String key) {
        return containsCustomValue(key);
    }

    static CustomValue toCustom(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return new Cv(CustomValue.CvType.NULL, null);
        }
        if (e.isJsonObject()) {
            Map<String, CustomValue> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                map.put(x.getKey(), toCustom(x.getValue()));
            }
            return new CvObj(map);
        }
        if (e.isJsonArray()) {
            List<CustomValue> list = new ArrayList<>();
            for (JsonElement x : (JsonArray) e) {
                list.add(toCustom(x));
            }
            return new CvArr(list);
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return new Cv(CustomValue.CvType.BOOLEAN, p.getAsBoolean());
        }
        if (p.isNumber()) {
            return new Cv(CustomValue.CvType.NUMBER, p.getAsNumber());
        }
        return new Cv(CustomValue.CvType.STRING, p.getAsString());
    }

    static class Cv implements CustomValue {
        final CvType type;
        final Object value;

        Cv(CvType type, Object value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public CvType getType() {
            return type;
        }

        @Override
        public CvObject getAsObject() {
            throw new ClassCastException("Not an object: " + type);
        }

        @Override
        public CvArray getAsArray() {
            throw new ClassCastException("Not an array: " + type);
        }

        @Override
        public String getAsString() {
            if (type != CvType.STRING) {
                throw new ClassCastException("Not a string: " + type);
            }
            return (String) value;
        }

        @Override
        public Number getAsNumber() {
            if (type != CvType.NUMBER) {
                throw new ClassCastException("Not a number: " + type);
            }
            return (Number) value;
        }

        @Override
        public boolean getAsBoolean() {
            if (type != CvType.BOOLEAN) {
                throw new ClassCastException("Not a boolean: " + type);
            }
            return (Boolean) value;
        }
    }

    static final class CvObj extends Cv implements CustomValue.CvObject {
        private final Map<String, CustomValue> map;

        CvObj(Map<String, CustomValue> map) {
            super(CvType.OBJECT, null);
            this.map = map;
        }

        @Override
        public CvObject getAsObject() {
            return this;
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
        }

        @Override
        public CustomValue get(String key) {
            return map.get(key);
        }

        @Override
        public Iterator<Map.Entry<String, CustomValue>> iterator() {
            return Collections.unmodifiableMap(map).entrySet().iterator();
        }
    }

    static final class CvArr extends Cv implements CustomValue.CvArray {
        private final List<CustomValue> list;

        CvArr(List<CustomValue> list) {
            super(CvType.ARRAY, null);
            this.list = list;
        }

        @Override
        public CvArray getAsArray() {
            return this;
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public CustomValue get(int index) {
            return list.get(index);
        }

        @Override
        public Iterator<CustomValue> iterator() {
            return Collections.unmodifiableList(list).iterator();
        }
    }

    static final class SimplePerson implements Person {
        private final String name;

        SimplePerson(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ContactInformation getContact() {
            return new Contact(Collections.emptyMap());
        }
    }

    static final class Contact implements ContactInformation {
        private final Map<String, String> map;

        Contact(Map<String, String> map) {
            this.map = map;
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(map.get(key));
        }

        @Override
        public Map<String, String> asMap() {
            return Collections.unmodifiableMap(map);
        }
    }
}
