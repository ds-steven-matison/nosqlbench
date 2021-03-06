package io.nosqlbench.activitytype.http;

import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class HttpConsoleFormats {

    private static final String CYCLE_CUE = "==== ";
    private static final String REQUEST_CUE = " ==> ";
    private static final String RESPONSE_CUE = " <== ";
    private static final String MESSAGE_CUE = "---- ";
    private static final String COMPONENT_CUE = " === ";
    private static final String DETAIL_CUE = "   - ";
    private static final String ENTRY_CUE = "   - ";
    private static final String PAYLOAD_CUE = "data:";

    private final long mask;
    private final long modulo;
    private final String enabled;
    private String filter;

    public String getFilter() {
        return this.filter;
    }

    private final static long _STATS = 1L;
    private final static long _REQUESTS = 1L << 1;
    private final static long _RESPONSES = 1L << 2;
    private final static long _HEADERS = 1L << 3;
    private final static long _REDIRECTS = 1L << 4;
    private final static long _DATA = 1L << 5;
    private final static long _DATA10 = 1L << 6;
    private final static long _DATA100 = 1L << 7;
    private final static long _DATA1000 = 1L << 8;

    enum Diag {

        headers(_HEADERS),
        stats(_STATS),
        data(_DATA),
        data10(_DATA10),
        data100(_DATA100),
        data1000(_DATA1000),
        redirects(_REDIRECTS),
        requests(_REQUESTS),
        responses(_RESPONSES),
        brief(_HEADERS | _STATS | _REQUESTS | _RESPONSES | _DATA10),
        all(_HEADERS | _STATS | _REDIRECTS | _REQUESTS | _RESPONSES | _DATA);

        private final long mask;

        Diag(long mask) {
            this.mask = mask;
        }

        public long addTo(long othermask) {
            return othermask | this.mask;
        }

        public boolean includedIn(long component) {
            return (mask & component) > 0L;
        }


        public static boolean anyIncluded(long mask, Diag... levels) {
            for (Diag level : levels) {
                if (level.includedIn(mask)) {
                    return true;
                }
            }
            return false;
        }

        public static String enabledSummary(long includedMask) {
            if (includedMask <= 0L) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Diag d : values()) {
                if ((d.mask & includedMask) == d.mask) {
                    sb.append(d).append(",");
                }
            }
            sb.setLength(sb.length() - 1);
            return sb.toString();
        }
    }

    public static HttpConsoleFormats apply(String spec, HttpConsoleFormats extant) {
        if (extant == null || (extant.getFilter() != null && !extant.getFilter().equals(spec))) {
            return new HttpConsoleFormats(spec);
        } else {
            return extant;
        }
    }

    private HttpConsoleFormats(String spec) {
        Set<String> filterSet = Set.of();
        if (spec != null) {
            filterSet = new HashSet<>(Arrays.asList(spec.split(",")));
        }

        long mod = 1L;
        Set<String> incl = new HashSet<>();
        long mask = 0L;

        for (String include : filterSet) {
            if (include.matches("[0-9]+")) {
                mod = Long.parseLong(include);
            } else {
                Diag diag = null;
                try {
                    diag = Diag.valueOf(include);
                    mask = diag.addTo(mask);
                } catch (Exception e) {
                    throw new RuntimeException("Invalid http diagnostic filter '" + include + "', choose from " +
                            Arrays.toString(Diag.values()));

                }
            }
        }
        this.mask = mask;
        this.modulo = mod;
        this.enabled = Diag.enabledSummary(mask);
    }

    public void summarizeResponseChain(Exception e, HttpResponse<String> lastResponse, PrintStream out, long cycle, long nanos) {
        if ((cycle % modulo) != 0) {
            return;
        }

        out.println(CYCLE_CUE + "DIAGNOSTICS (cycle " + cycle + ") (filters " + enabled + ")");

        LinkedList<HttpResponse<String>> responses = new LinkedList<>();
        HttpResponse<String> walking = lastResponse;

        while (lastResponse != null) {
            responses.add(lastResponse);
            lastResponse = lastResponse.previousResponse().orElse(null);
        }


        Iterator<HttpResponse<String>> iter = responses.descendingIterator();
        int index = 0;
        while (iter.hasNext()) {
            index++;
            HttpResponse<String> resp = iter.next();
            if (Diag.requests.includedIn(mask) && (Diag.redirects.includedIn(mask) || index == 1)) {
                summarizeRequest("REQUEST [" + index + "]", null, resp.request(), out, cycle, nanos);
            }

            if (Diag.stats.includedIn(mask) && index == 1) {
                out.println(DETAIL_CUE + "redirects = " + (responses.size() - 1));
            }

            if (Diag.responses.includedIn(mask) && (Diag.redirects.includedIn(mask) || index == responses.size())) {
                summarizeResponse("RESPONSE[" + index + "]", null, resp, out, cycle, nanos);
            }
        }
    }

    public void summarizeRequest(String caption, Exception e, HttpRequest request, PrintStream out, long cycle, long nanos) {
        if ((cycle % modulo) != 0) {
            return;
        }

        out.println(REQUEST_CUE + (caption != null ? caption : " REQUEST"));

        if (e != null) {
            out.println(CYCLE_CUE + " EXCEPTION: " + e.getMessage());
        }

        out.println(COMPONENT_CUE + request.method() + " " + request.uri() + " " + request.version().orElse(HttpClient.Version.HTTP_2));
        summariseHeaders(request.headers(), out);
        out.println(DETAIL_CUE + "body length:" + request.bodyPublisher().get().contentLength());
    }

    public void summarizeResponse(String caption, Exception e, HttpResponse<String> response, PrintStream out, long cycle, long nanos) {
        if ((cycle % modulo) != 0) {
            return;
        }

        out.println(RESPONSE_CUE + (caption != null ? caption : " RESPONSE") +
                " status=" + response.statusCode() + " took=" + (nanos / 1_000_000) + "ms");

        if (e != null) {
            out.println(MESSAGE_CUE + " EXCEPTION: " + e.getMessage());
        }

        summariseHeaders(response.headers(), out);
        summarizeContent(response, out);

    }

    private void summarizeContent(HttpResponse<String> response, PrintStream out) {
        if (Diag.anyIncluded(mask, Diag.data, Diag.data10, Diag.data100, Diag.data1000)) {

            String contentLenStr = response.headers().map().getOrDefault("content-length", List.of("0")).get(0);
            Long contentLength = Long.parseLong(contentLenStr);
            if (contentLength == 0L) {
                return;
            }

            System.out.println(PAYLOAD_CUE);
            List<String> contentTypeList = response.headers().map().getOrDefault("content-type", List.of("text/html"));
            String printable = "<non-printable>";
            if (contentTypeList.size() > 1) {
                printable = "non-printable/multiple content types provided";
            } else {
                String contentType = contentTypeList.get(0).toLowerCase();
                if (!contentType.contains("text") && !contentType.contains("json")) {
                    printable = "non-printable content type:" + contentTypeList.get(0);
                } else {
                    printable = response.body();
                    if (printable == null) {
                        printable = "content-length was " + contentLength + ", but body was null";
                    }

                    if (Diag.data1000.includedIn(mask)) {
                        if (printable.length() > 1000) {
                            printable = printable.substring(0, 1000) + "\n--truncated at 1000 characters--\n";
                        }
                    } else if (Diag.data100.includedIn(mask)) {
                        if (printable.length() > 100) {
                            printable = printable.substring(0, 100) + "\ntruncated at 100 characters\n";
                        }
                    } else if (Diag.data10.includedIn(mask)) {
                        if (printable.length() > 10) {
                            printable = printable.substring(0, 10) + "\ntruncated at 10 characters\n";
                        }
                    }
                }
            }
            System.out.println(printable);
        }
    }

    private void summariseHeaders(HttpHeaders headers, PrintStream out) {
        if (!Diag.headers.includedIn(mask)) {
            return;
        }

        out.println(COMPONENT_CUE + "headers(" + headers.map().keySet().size() + ")");
        headers.map().forEach((k, v) -> {
            out.print(DETAIL_CUE + k + ":");
            if (v.size() > 1) {
                out.println();
                v.forEach(h -> {
                    out.println(ENTRY_CUE + h);
                });
            } else {
                out.println(" " + v.get(0));
            }
        });
    }

    public boolean isDiagnosticMode() {
        return this.mask > 0L;
    }
}
