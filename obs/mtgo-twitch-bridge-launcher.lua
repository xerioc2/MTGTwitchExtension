obs = obslua

local bridge_path = ""
local sidecar_name = "mtgo-twitch-bridge-launcher.path.txt"

function script_description()
    return "Launches MTGO Twitch Bridge when streaming or recording starts. Optional convenience script; configure the bridge .exe path in this script's properties."
end

function script_properties()
    local props = obs.obs_properties_create()
    obs.obs_properties_add_path(
        props,
        "bridge_path",
        "MTGO Twitch Bridge.exe",
        obs.OBS_PATH_FILE,
        "Executable (*.exe)",
        nil
    )
    return props
end

function script_update(settings)
    bridge_path = obs.obs_data_get_string(settings, "bridge_path") or ""
    if bridge_path == "" then
        bridge_path = read_sidecar_path()
        if bridge_path ~= "" then
            obs.obs_data_set_string(settings, "bridge_path", bridge_path)
        end
    end
end

function script_save(settings)
    obs.obs_data_set_string(settings, "bridge_path", bridge_path)
end

function script_load(settings)
    script_update(settings)
    obs.obs_frontend_add_event_callback(on_frontend_event)
end

function script_unload()
    obs.obs_frontend_remove_event_callback(on_frontend_event)
end

function on_frontend_event(event)
    if event == obs.OBS_FRONTEND_EVENT_STREAMING_STARTED
        or event == obs.OBS_FRONTEND_EVENT_RECORDING_STARTED then
        launch_bridge()
    end
end

function launch_bridge()
    if bridge_path == nil or bridge_path == "" then
        return
    end

    local safe_path = string.gsub(bridge_path, '"', '')
    os.execute('start "" "' .. safe_path .. '" --quiet-if-running')
end

function has_trailing_separator(directory)
    local last = string.sub(directory, -1)
    return last == "/" or last == "\\"
end

function read_sidecar_path()
    local directory = script_directory()
    if directory == "" then
        return ""
    end

    local base = directory
    if not has_trailing_separator(base) then
        base = base .. "\\"
    end

    local file = io.open(base .. sidecar_name, "r")
    if file == nil then
        file = io.open(directory .. "/" .. sidecar_name, "r")
    end
    if file == nil then
        return ""
    end

    local contents = file:read("*a") or ""
    file:close()
    return trim(contents)
end

-- script_path() is an OBS-provided global that already returns the script's
-- containing directory (with a trailing separator) -- it must NOT be run
-- through the "strip last path segment" logic below, which is only valid for
-- a full *file* path (the debug.getinfo fallback).
function script_directory()
    if type(script_path) == "function" then
        local dir = script_path()
        if dir ~= nil and dir ~= "" then
            return dir
        end
    end

    local source = debug.getinfo(1, "S").source or ""
    if string.sub(source, 1, 1) == "@" then
        local file_path = string.sub(source, 2)
        return string.match(file_path, "^(.*)[/\\][^/\\]+$") or ""
    end

    return ""
end

function trim(value)
    return (value:gsub("^%s+", ""):gsub("%s+$", ""))
end
