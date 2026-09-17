import React, { useMemo, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Linking,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { api } from "./src/api";

function Button({ title, onPress, disabled = false, secondary = false }) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        secondary && styles.buttonSecondary,
        disabled && styles.buttonDisabled,
        pressed && !disabled && { opacity: 0.8 }
      ]}
    >
      <Text style={[styles.buttonText, secondary && styles.buttonTextSecondary]}>{title}</Text>
    </Pressable>
  );
}

function ErrorBox({ message }) {
  if (!message) return null;
  return <Text style={styles.error}>{message}</Text>;
}

export default function App() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [screen, setScreen] = useState("search");
  const [title, setTitle] = useState(null);
  const [sources, setSources] = useState([]);
  const [season, setSeason] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [episode, setEpisode] = useState(null);

  const heading = useMemo(() => {
    if (screen === "search") return "TheNkiri";
    if (screen === "title") return title?.title || "Title";
    if (screen === "episodes") return `${title?.title || "Series"} • Season ${season}`;
    if (screen === "qualities") return episode ? `${title?.title} • ${episode.label}` : title?.title || "Quality";
    return "TheNkiri";
  }, [screen, title, season, episode]);

  async function runSearch() {
    const q = query.trim();
    if (q.length < 2) return;
    setLoading(true);
    setError("");
    try {
      const data = await api.search(q);
      setResults(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function openTitle(item) {
    setLoading(true);
    setError("");
    try {
      const info = await api.title(item.id);
      setTitle(info);
      setEpisode(null);
      setSeason(null);
      setSources([]);
      setScreen("title");

      if (info.type === "movie") {
        const sourceData = await api.sources(info.id);
        setSources(sourceData.sources || []);
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function openSeason(seasonNumber) {
    setLoading(true);
    setError("");
    try {
      const data = await api.episodes(title.id, seasonNumber);
      setSeason(seasonNumber);
      setEpisodes(data.episodes || []);
      setScreen("episodes");
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function openEpisode(item) {
    setLoading(true);
    setError("");
    try {
      const data = await api.sources(title.id, {
        season: item.season,
        episode: item.episode
      });
      setEpisode(item);
      setSources(data.sources || []);
      setScreen("qualities");
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  async function download(source) {
    try {
      if (!source?.url) throw new Error("No download URL was returned.");
      const supported = await Linking.canOpenURL(source.url);
      if (!supported) throw new Error("Android could not open this download URL.");
      await Linking.openURL(source.url);
    } catch (e) {
      setError(e.message);
    }
  }

  function goBack() {
    setError("");
    if (screen === "qualities") return setScreen("episodes");
    if (screen === "episodes") return setScreen("title");
    if (screen === "title") return setScreen("search");
  }

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" />
      <View style={styles.header}>
        {screen !== "search" ? (
          <Pressable onPress={goBack} style={styles.backButton}>
            <Text style={styles.backText}>‹ Back</Text>
          </Pressable>
        ) : <View style={styles.backPlaceholder} />}
        <Text style={styles.headerTitle} numberOfLines={1}>{heading}</Text>
        <View style={styles.backPlaceholder} />
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <ErrorBox message={error} />

        {screen === "search" && (
          <>
            <Text style={styles.hero}>Movies, series & K-Drama</Text>
            <Text style={styles.subhero}>Search. Pick a title. Choose quality. Download.</Text>

            <View style={styles.searchRow}>
              <TextInput
                value={query}
                onChangeText={setQuery}
                onSubmitEditing={runSearch}
                placeholder="Search Toy Story, Breaking Bad…"
                placeholderTextColor="#777"
                style={styles.input}
                returnKeyType="search"
              />
              <Button title="Search" onPress={runSearch} disabled={loading || query.trim().length < 2} />
            </View>

            {results.map(item => (
              <Pressable key={`${item.id}-${item.type}`} style={styles.card} onPress={() => openTitle(item)}>
                {item.poster ? <Image source={{ uri: item.poster }} style={styles.posterSmall} /> : <View style={styles.posterSmall} />}
                <View style={styles.cardBody}>
                  <Text style={styles.cardTitle}>{item.title}</Text>
                  <Text style={styles.meta}>
                    {[item.year, item.type === "series" ? "Series" : "Movie", item.rating ? `★ ${item.rating}` : null]
                      .filter(Boolean)
                      .join(" • ")}
                  </Text>
                  {!!item.genre && <Text style={styles.genre} numberOfLines={2}>{item.genre}</Text>}
                </View>
              </Pressable>
            ))}
          </>
        )}

        {screen === "title" && title && (
          <>
            {title.poster ? <Image source={{ uri: title.poster }} style={styles.posterLarge} /> : null}
            <Text style={styles.title}>{title.title}</Text>
            <Text style={styles.meta}>
              {[title.year, title.rating ? `★ ${title.rating}` : null, title.country].filter(Boolean).join(" • ")}
            </Text>
            {!!title.genre && <Text style={styles.genre}>{title.genre}</Text>}
            {!!title.description && <Text style={styles.description}>{title.description}</Text>}

            {title.type === "series" ? (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Seasons</Text>
                {(title.seasons || []).map(item => (
                  <Button
                    key={item.season}
                    title={`Season ${item.season} • ${item.maxEp} episodes`}
                    onPress={() => openSeason(item.season)}
                  />
                ))}
              </View>
            ) : (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Choose quality</Text>
                {sources.map(source => (
                  <Button
                    key={`${source.quality}-${source.size}`}
                    title={`${source.quality || "?"}p${source.sizeText ? ` • ${source.sizeText}` : ""}`}
                    onPress={() => download(source)}
                  />
                ))}
              </View>
            )}
          </>
        )}

        {screen === "episodes" && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Episodes</Text>
            <View style={styles.episodeGrid}>
              {episodes.map(item => (
                <Pressable key={item.label} style={styles.episodeButton} onPress={() => openEpisode(item)}>
                  <Text style={styles.episodeText}>{item.label}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        {screen === "qualities" && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Choose download quality</Text>
            {sources.map(source => (
              <Button
                key={`${source.quality}-${source.size}`}
                title={`${source.quality || "?"}p${source.sizeText ? ` • ${source.sizeText}` : ""}`}
                onPress={() => download(source)}
              />
            ))}
          </View>
        )}

        {loading && (
          <View style={styles.loading}>
            <ActivityIndicator size="large" />
            <Text style={styles.loadingText}>Loading…</Text>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#0b0b0d" },
  header: { height: 58, flexDirection: "row", alignItems: "center", borderBottomWidth: 1, borderBottomColor: "#202024", paddingHorizontal: 14 },
  headerTitle: { flex: 1, textAlign: "center", color: "#fff", fontWeight: "800", fontSize: 18 },
  backButton: { width: 72 },
  backPlaceholder: { width: 72 },
  backText: { color: "#fff", fontSize: 16 },
  content: { padding: 16, paddingBottom: 48 },
  hero: { color: "#fff", fontSize: 30, fontWeight: "900", marginTop: 16 },
  subhero: { color: "#a7a7ad", fontSize: 15, marginTop: 8, marginBottom: 20 },
  searchRow: { gap: 10, marginBottom: 18 },
  input: { backgroundColor: "#17171b", borderWidth: 1, borderColor: "#29292f", borderRadius: 14, color: "#fff", paddingHorizontal: 14, paddingVertical: 14, fontSize: 16 },
  button: { backgroundColor: "#ffffff", borderRadius: 12, paddingVertical: 13, paddingHorizontal: 15, marginTop: 10, alignItems: "center" },
  buttonSecondary: { backgroundColor: "#1b1b20" },
  buttonDisabled: { opacity: 0.45 },
  buttonText: { color: "#0b0b0d", fontWeight: "800", fontSize: 15 },
  buttonTextSecondary: { color: "#fff" },
  card: { flexDirection: "row", gap: 12, backgroundColor: "#151519", borderRadius: 14, padding: 10, marginBottom: 12, borderWidth: 1, borderColor: "#202026" },
  posterSmall: { width: 76, height: 108, borderRadius: 10, backgroundColor: "#222" },
  cardBody: { flex: 1, justifyContent: "center" },
  cardTitle: { color: "#fff", fontSize: 17, fontWeight: "800" },
  meta: { color: "#b7b7bc", marginTop: 5, fontSize: 13 },
  genre: { color: "#8e8e95", marginTop: 7, lineHeight: 18 },
  posterLarge: { width: "100%", height: 420, borderRadius: 18, backgroundColor: "#222", marginTop: 8 },
  title: { color: "#fff", fontSize: 28, fontWeight: "900", marginTop: 18 },
  description: { color: "#d0d0d5", marginTop: 16, lineHeight: 23, fontSize: 15 },
  section: { marginTop: 22 },
  sectionTitle: { color: "#fff", fontSize: 20, fontWeight: "900", marginBottom: 6 },
  episodeGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  episodeButton: { backgroundColor: "#1b1b20", borderWidth: 1, borderColor: "#2d2d34", paddingVertical: 13, paddingHorizontal: 16, borderRadius: 12 },
  episodeText: { color: "#fff", fontWeight: "700" },
  error: { color: "#ff7676", backgroundColor: "#2a1214", borderRadius: 10, padding: 12, marginBottom: 12 },
  loading: { alignItems: "center", marginTop: 30, gap: 10 },
  loadingText: { color: "#aaa" }
});
