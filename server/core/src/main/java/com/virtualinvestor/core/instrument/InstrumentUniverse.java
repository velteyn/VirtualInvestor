package com.virtualinvestor.core.instrument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The fixed instrument universe, loaded from a CSV resource. Parsing is header-driven so
 * columns can be reordered; tickers are unique and iterated in file order for determinism.
 */
public final class InstrumentUniverse {

    public static final String DEFAULT_RESOURCE = "instruments.csv";

    private final List<Instrument> instruments;
    private final Map<String, Instrument> byTicker;
    private final Instrument index;

    private InstrumentUniverse(List<Instrument> instruments) {
        this.instruments = List.copyOf(instruments);
        Map<String, Instrument> map = new LinkedHashMap<>();
        for (Instrument instrument : instruments) {
            if (map.putIfAbsent(instrument.ticker(), instrument) != null) {
                throw new IllegalArgumentException("duplicate ticker: " + instrument.ticker());
            }
        }
        this.byTicker = Collections.unmodifiableMap(map);

        Instrument foundIndex = null;
        for (Instrument instrument : instruments) {
            if (instrument.isIndex()) {
                if (foundIndex != null) {
                    throw new IllegalArgumentException("more than one index in universe: "
                            + foundIndex.ticker() + ", " + instrument.ticker());
                }
                foundIndex = instrument;
            }
        }
        this.index = foundIndex;
    }

    public static InstrumentUniverse defaultUniverse() {
        return fromClasspath(DEFAULT_RESOURCE);
    }

    /** Build a universe directly from instruments (tests, synthetic scenarios). */
    public static InstrumentUniverse of(List<Instrument> instruments) {
        return new InstrumentUniverse(instruments);
    }

    public static InstrumentUniverse fromClasspath(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = InstrumentUniverse.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("instrument resource not found: " + resource);
            }
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading " + resource, e);
        }
    }

    public static InstrumentUniverse parse(InputStream in) {
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(line -> !line.isBlank()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading instrument data", e);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("instrument data is empty");
        }
        String[] header = lines.get(0).split(",", -1);
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.put(header[i].trim(), i);
        }
        List<Instrument> parsed = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);
            parsed.add(new Instrument(
                    cell(cells, columns, "ticker"),
                    cell(cells, columns, "name"),
                    cell(cells, columns, "sector"),
                    InstrumentType.valueOf(cell(cells, columns, "type")),
                    Long.parseLong(cell(cells, columns, "sharesOutstanding")),
                    Double.parseDouble(cell(cells, columns, "freeFloat")),
                    Double.parseDouble(cell(cells, columns, "tickSize")),
                    Long.parseLong(cell(cells, columns, "quantityGrid")),
                    Double.parseDouble(cell(cells, columns, "referencePrice")),
                    Double.parseDouble(cell(cells, columns, "baseVolatility")),
                    Double.parseDouble(cell(cells, columns, "marketBeta")),
                    Double.parseDouble(cell(cells, columns, "sectorBeta"))));
        }
        return new InstrumentUniverse(parsed);
    }

    private static String cell(String[] cells, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IllegalArgumentException("missing column: " + name);
        }
        if (index >= cells.length) {
            throw new IllegalArgumentException("row is missing value for column: " + name);
        }
        return cells[index].trim();
    }

    public List<Instrument> all() {
        return instruments;
    }

    public List<Instrument> equities() {
        return instruments.stream().filter(instrument -> !instrument.isIndex()).toList();
    }

    public Optional<Instrument> find(String ticker) {
        return Optional.ofNullable(byTicker.get(ticker));
    }

    public Optional<Instrument> index() {
        return Optional.ofNullable(index);
    }

    public int size() {
        return instruments.size();
    }
}
