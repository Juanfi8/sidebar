import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import { useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Alert, AppState, Pressable, ScrollView, Switch, Text, TextInput, View } from "react-native";
import Sidebar, { InstalledApp, OverlaySettings } from "../../modules/sidebar";
import { AppColors, AppStyles } from "../theme";

type MediaSettings = Pick<OverlaySettings, "showMediaPlayer" | "mediaApps">;

export function MediaPlayerSettings({ settings, onChange, colors, s }: {
  settings: MediaSettings;
  onChange: (settings: MediaSettings) => void;
  colors: AppColors;
  s: AppStyles;
}) {
  const [hasAccess, setHasAccess] = useState<boolean | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [apps, setApps] = useState<InstalledApp[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [query, setQuery] = useState("");

  useEffect(() => {
    let mounted = true;
    const check = () => Sidebar.hasMediaAccessPermission()
      .then(granted => { if (mounted) setHasAccess(granted); })
      .catch(() => { if (mounted) setHasAccess(false); });
    check();
    const listener = AppState.addEventListener("change", state => { if (state === "active") check(); });
    return () => { mounted = false; listener.remove(); };
  }, []);

  useEffect(() => {
    if (!expanded || apps !== null) return;
    let mounted = true;
    setLoading(true);
    setError(false);
    Sidebar.getInstalledApps()
      .then(items => {
        if (mounted) {
          setApps([...new Map(items.map(app => [app.packageName, app])).values()]);
          setLoading(false);
        }
      })
      .catch(() => {
        if (mounted) { setError(true); setLoading(false); }
      });
    return () => { mounted = false; };
  }, [expanded, apps, retry]);

  const selected = useMemo(() => new Set(settings.mediaApps), [settings.mediaApps]);
  const visibleApps = useMemo(() => {
    const installed = apps ?? [];
    const installedPackages = new Set(installed.map(app => app.packageName));
    const missing = settings.mediaApps.filter(pkg => !installedPackages.has(pkg))
      .map(packageName => ({ packageName, name: packageName, icon: "" }));
    const search = query.trim().toLowerCase();
    return [...installed, ...missing].filter(app =>
      app.name.toLowerCase().includes(search) || app.packageName.toLowerCase().includes(search));
  }, [apps, settings.mediaApps, query]);

  return (
    <View style={[s.section, { marginTop: 12 }]}>
      <Text style={s.sectionTitle}>Media Player</Text>
      <View style={s.row}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowLabel}>Show in Quick Controls</Text>
          <Text style={s.rowSub}>Album art, song, artist, and previous / play / next controls</Text>
        </View>
        <Switch
          accessibilityLabel="Show media player in Quick Controls"
          value={settings.showMediaPlayer}
          onValueChange={showMediaPlayer => onChange({ ...settings, showMediaPlayer })}
          trackColor={{ true: colors.tint }}
        />
      </View>
      <View style={s.separator} />
      <View style={s.row}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowLabel}>Notification access</Text>
          <Text style={s.rowSub}>Android requires this to access media sessions. Only your selected apps appear in the player.</Text>
        </View>
        {hasAccess
          ? <Ionicons name="checkmark-circle" size={20} color={colors.primary} />
          : hasAccess === null
            ? <ActivityIndicator color={colors.primary} />
            : <Pressable style={s.grantBtn} onPress={() => Sidebar.requestMediaAccessPermission()
              .catch(() => Alert.alert("Media access", "Open notification access in Android settings."))}>
              <Text style={s.grantBtnText}>Grant</Text>
            </Pressable>}
      </View>
      <View style={s.separator} />
      <Pressable style={s.row} accessibilityRole="button" accessibilityState={{ expanded }} onPress={() => setExpanded(value => !value)}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowLabel}>Media apps</Text>
          <Text style={s.rowSub}>{selected.size} selected — choose which apps the player can control</Text>
        </View>
        <Ionicons name={expanded ? "chevron-up" : "chevron-down"} size={20} color={colors.subtext} />
      </Pressable>
      {expanded && (
        <View style={{ paddingHorizontal: 16, paddingBottom: 12 }}>
          <TextInput
            accessibilityLabel="Search media apps"
            value={query}
            onChangeText={setQuery}
            placeholder="Search installed apps"
            placeholderTextColor={colors.subtext}
            autoCorrect={false}
            style={[s.search, { marginHorizontal: 0 }]}
          />
          {loading ? <ActivityIndicator color={colors.primary} style={{ marginVertical: 16 }} />
            : error ? <Pressable style={s.grantBtn} onPress={() => setRetry(value => value + 1)}>
              <Text style={s.grantBtnText}>Retry loading apps</Text>
            </Pressable>
              : <ScrollView style={{ maxHeight: 260 }} nestedScrollEnabled keyboardShouldPersistTaps="handled">
                {visibleApps.map(app => (
                  <Pressable
                    key={app.packageName}
                    accessibilityRole="checkbox"
                    accessibilityLabel={app.name}
                    accessibilityState={{ checked: selected.has(app.packageName) }}
                    style={[s.row, { paddingHorizontal: 0, paddingVertical: 10, gap: 10 }]}
                    onPress={() => onChange({
                      ...settings,
                      mediaApps: selected.has(app.packageName)
                        ? settings.mediaApps.filter(pkg => pkg !== app.packageName)
                        : [...settings.mediaApps, app.packageName],
                    })}
                  >
                    {app.icon ? <Image source={{ uri: `data:image/png;base64,${app.icon}` }} style={{ width: 32, height: 32, borderRadius: 8 }} />
                      : <Ionicons name="musical-notes-outline" size={28} color={colors.subtext} />}
                    <Text style={[s.rowLabel, { flex: 1 }]} numberOfLines={1}>{app.name}</Text>
                    <Ionicons name={selected.has(app.packageName) ? "checkbox" : "square-outline"} size={24} color={selected.has(app.packageName) ? colors.primary : colors.subtext} />
                  </Pressable>
                ))}
                {visibleApps.length === 0 && <Text style={s.rowSub}>No matching apps</Text>}
              </ScrollView>}
          <Text style={[s.rowSub, { marginTop: 10 }]}>Start playback in a selected app that supports Android media controls. Playing sessions take priority; paused tracks can be resumed.</Text>
        </View>
      )}
    </View>
  );
}
