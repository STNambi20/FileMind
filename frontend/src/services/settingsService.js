import api from "./api";

export async function getSettings() {
  const { data } = await api.get("/settings");
  return data.data;
}

export async function updatePreferences(payload) {
  const { data } = await api.patch("/settings/preferences", payload);
  return data.data;
}

export async function updateAccount(payload) {
  const { data } = await api.patch("/settings/account", payload);
  return data.data;
}
