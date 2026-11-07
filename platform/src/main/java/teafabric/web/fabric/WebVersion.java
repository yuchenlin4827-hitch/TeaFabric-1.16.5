package teafabric.web.fabric;

import net.fabricmc.loader.api.Version;

/** Version with a dotted-numeric-aware comparison; enough for the version checks mods do at runtime. */
final class WebVersion implements Version {
    private final String text;

    WebVersion(String text) {
        this.text = text;
    }

    @Override
    public String getFriendlyString() {
        return text;
    }

    @Override
    public int compareTo(Version o) {
        String a = text.split("[+]")[0];
        String b = o.getFriendlyString().split("[+]")[0];
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String x = i < pa.length ? pa[i] : "0";
            String y = i < pb.length ? pb[i] : "0";
            int c;
            try {
                c = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
            } catch (NumberFormatException e) {
                c = x.compareTo(y);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version && ((Version) o).getFriendlyString().equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
