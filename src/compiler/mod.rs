use crate::parser::{Component, NavigationItem, ParsedApp, Route};
use serde::Serialize;

#[derive(Serialize)]
struct MeteoroApp {
    #[serde(rename = "appName")]
    app_name: &'static str,
    version: &'static str,
    screens: Vec<Component>,
    #[serde(rename = "initialRoute", skip_serializing_if = "Option::is_none")]
    initial_route: Option<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    routes: Vec<Route>,
    #[serde(rename = "navigationBar", skip_serializing_if = "Vec::is_empty")]
    navigation_bar: Vec<NavigationItem>,
}

pub fn compile(app: ParsedApp) -> Result<String, serde_json::Error> {
    serde_json::to_string_pretty(&MeteoroApp {
        app_name: "Meteoro App",
        version: "0.0.1",
        screens: app.screens,
        initial_route: app.initial_route,
        routes: app.routes,
        navigation_bar: app.navigation_bar,
    })
}
