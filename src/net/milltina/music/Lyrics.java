package net.milltina.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    public static final Lyrics NONE = new Lyrics(new ArrayList<Line>());

    public final List<Line> lines;

    private Lyrics(List<Line> lines) {
        this.lines = lines;
    }

    public static Lyrics parse(String lrc, String translation) {
        List<Line> lines = read(lrc);
        List<Line> translated = read(translation);

        int t = 0;
        for (Line line : lines) {
            while (t < translated.size() && translated.get(t).time < line.time - 50L) {
                t++;
            }
            if (t < translated.size() && Math.abs(translated.get(t).time - line.time) <= 50L) {
                line.translation = translated.get(t).text;
            }
        }
        return lines.isEmpty() ? NONE : new Lyrics(lines);
    }

    private static List<Line> read(String lrc) {
        List<Line> lines = new ArrayList<Line>();
        if (lrc == null) {
            return lines;
        }
        for (String raw : lrc.split("\r?\n")) {
            Matcher matcher = TAG.matcher(raw);
            List<Long> times = new ArrayList<Long>();
            int end = 0;

            while (matcher.find() && matcher.start() == end) {
                long ms = Long.parseLong(matcher.group(1)) * 60000L + Long.parseLong(matcher.group(2)) * 1000L;
                String fraction = matcher.group(3);
                if (fraction != null) {
                    ms += Long.parseLong(fraction) * (fraction.length() == 1 ? 100 : fraction.length() == 2 ? 10 : 1);
                }
                times.add(Long.valueOf(ms));
                end = matcher.end();
            }
            String text = raw.substring(end).trim();
            if (times.isEmpty() || text.length() == 0) {
                continue;
            }
            for (Long time : times) {
                lines.add(new Line(time.longValue(), text));
            }
        }
        Collections.sort(lines, (a, b) -> Long.compare(a.time, b.time));
        return lines;
    }

    public int indexAt(long ms) {
        int low = 0;
        int high = this.lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (this.lines.get(middle).time <= ms) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    public static final class Line {
        public final long time;
        public final String text;
        public String translation;

        Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}
