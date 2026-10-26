use serde::{Deserialize, Serialize};
use std::error::Error;
use std::fmt;

#[derive(Debug, Serialize, Deserialize)]
pub struct Component {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(rename = "type")]
    pub type_name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub value: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub props: Option<ComponentProps>,
}

#[derive(Debug, Serialize, Deserialize, Default)]
pub struct ComponentProps {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub action: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub placeholder: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub color: Option<String>,
    #[serde(rename = "backgroundColor", skip_serializing_if = "Option::is_none")]
    pub background_color: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub size: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub padding: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rounded: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub alignment: Option<String>,
    #[serde(rename = "fontWeight", skip_serializing_if = "Option::is_none")]
    pub font_weight: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub direction: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub spacing: Option<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scrollable: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub children: Option<Vec<Component>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct Route {
    pub id: String,
    pub title: String,
    pub components: Vec<Component>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NavigationItem {
    pub route: String,
    pub label: String,
}

#[derive(Debug, Default)]
pub struct ParsedApp {
    pub screens: Vec<Component>,
    pub routes: Vec<Route>,
    pub initial_route: Option<String>,
    pub navigation_bar: Vec<NavigationItem>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct ParseError {
    line: usize,
    message: String,
}

impl ParseError {
    fn new(line: usize, message: impl Into<String>) -> Self {
        Self {
            line,
            message: message.into(),
        }
    }
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "line {}: {}", self.line, self.message)
    }
}

impl Error for ParseError {}

struct OpenRoute {
    id: String,
    title: String,
    components: Vec<Component>,
}

pub fn parse(input: &str) -> Result<ParsedApp, ParseError> {
    let mut app = ParsedApp::default();
    let mut root_components = Vec::new();
    let mut current_route: Option<OpenRoute> = None;
    let mut open_containers: Vec<Component> = Vec::new();
    let mut navigation_targets = Vec::new();
    let mut navigation_routes = Vec::new();

    for (index, raw_line) in input.lines().enumerate() {
        let line_number = index + 1;
        let line = raw_line.trim();
        if line.is_empty() {
            continue;
        }

        let (command, arguments) = split_command(line, line_number)?;
        match command {
            "screen" => {
                if !open_containers.is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "close the open component with 'end' before starting another screen",
                    ));
                }
                if !root_components.is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "declare screens before adding root components",
                    ));
                }
                if let Some(route) = current_route.take() {
                    app.routes.push(Route {
                        id: route.id,
                        title: route.title,
                        components: route.components,
                    });
                }

                let (id, remainder) = parse_string_argument(arguments, line_number)?;
                if id.trim().is_empty() {
                    return Err(ParseError::new(line_number, "screen ID cannot be empty"));
                }
                let title = if remainder.is_empty() {
                    id.clone()
                } else {
                    let (title, trailing) = parse_string_argument(remainder, line_number)?;
                    if !trailing.is_empty() {
                        return Err(ParseError::new(
                            line_number,
                            "unexpected arguments after screen title",
                        ));
                    }
                    title
                };
                if app.routes.iter().any(|route| route.id == id)
                    || current_route.as_ref().is_some_and(|route| route.id == id)
                {
                    return Err(ParseError::new(
                        line_number,
                        format!("duplicate screen ID '{id}'"),
                    ));
                }
                current_route = Some(OpenRoute {
                    id,
                    title,
                    components: Vec::new(),
                });
            }
            "end" => {
                if !arguments.is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "'end' does not accept arguments",
                    ));
                }
                let Some(container) = open_containers.pop() else {
                    return Err(ParseError::new(
                        line_number,
                        "'end' must close an open card",
                    ));
                };
                add_component(
                    container,
                    &mut open_containers,
                    &mut current_route,
                    &mut root_components,
                );
            }
            "tab" | "navigation" => {
                if !open_containers.is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "navigation tabs must be declared outside containers",
                    ));
                }
                let (route, remainder) = parse_string_argument(arguments, line_number)?;
                let (label, trailing) = parse_string_argument(remainder, line_number)?;
                if !trailing.is_empty() || route.trim().is_empty() || label.trim().is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "navigation syntax is: navigation \"screen-id\" \"label\"",
                    ));
                }
                if app.navigation_bar.iter().any(|item| item.route == route) {
                    return Err(ParseError::new(
                        line_number,
                        format!("duplicate navigation tab for screen '{route}'"),
                    ));
                }
                navigation_routes.push((route.clone(), line_number));
                app.navigation_bar.push(NavigationItem { route, label });
            }
            "style" => {
                let component = last_component_mut(
                    &mut open_containers,
                    &mut current_route,
                    &mut root_components,
                )
                .ok_or_else(|| ParseError::new(line_number, "style must follow a component"))?;
                apply_style(arguments, line_number, component)?;
            }
            "row" | "column" | "list" | "scroll" => {
                open_containers.push(parse_container(command, arguments, line_number)?);
            }
            "text" | "button" | "input" | "card" | "image" => {
                let component = parse_component(command, arguments, line_number)?;
                if command == "button" {
                    if let Some(action) = component
                        .props
                        .as_ref()
                        .and_then(|props| props.action.as_ref())
                    {
                        if let Some(target) = action.strip_prefix("navigate:") {
                            navigation_targets.push((target.to_string(), line_number));
                        }
                    }
                }

                if command == "card" {
                    open_containers.push(component);
                } else {
                    add_component(
                        component,
                        &mut open_containers,
                        &mut current_route,
                        &mut root_components,
                    );
                }
            }
            _ => {
                return Err(ParseError::new(
                    line_number,
                    "unknown component; expected text, button, input, image, card, row, column, list, scroll, navigation or end",
                ));
            }
        }
    }

    if !open_containers.is_empty() {
        return Err(ParseError::new(
            input.lines().count(),
            "close the open component with 'end'",
        ));
    }
    if let Some(route) = current_route {
        app.routes.push(Route {
            id: route.id,
            title: route.title,
            components: route.components,
        });
    }
    app.initial_route = app.routes.first().map(|route| route.id.clone());
    app.screens = root_components;

    for (target, line_number) in navigation_targets {
        if !app.routes.iter().any(|route| route.id == target) {
            return Err(ParseError::new(
                line_number,
                format!("navigation target '{target}' is not a declared screen"),
            ));
        }
    }
    for (target, line_number) in navigation_routes {
        if !app.routes.iter().any(|route| route.id == target) {
            return Err(ParseError::new(
                line_number,
                format!("navigation tab target '{target}' is not a declared screen"),
            ));
        }
    }

    Ok(app)
}

fn split_command(line: &str, line_number: usize) -> Result<(&str, &str), ParseError> {
    if let Some(separator) = line.find(char::is_whitespace) {
        Ok((&line[..separator], line[separator..].trim()))
    } else if matches!(line, "end" | "row" | "column") {
        Ok((line, ""))
    } else {
        Err(ParseError::new(
            line_number,
            "expected a component followed by a quoted string",
        ))
    }
}

fn parse_string_argument<'a>(
    input: &'a str,
    line_number: usize,
) -> Result<(String, &'a str), ParseError> {
    let mut deserializer = serde_json::Deserializer::from_str(input).into_iter::<String>();
    let value = deserializer
        .next()
        .ok_or_else(|| ParseError::new(line_number, "expected a quoted string"))?
        .map_err(|_| ParseError::new(line_number, "expected a valid quoted string"))?;
    let offset = deserializer.byte_offset();
    Ok((value, input[offset..].trim()))
}

fn parse_component(
    command: &str,
    arguments: &str,
    line_number: usize,
) -> Result<Component, ParseError> {
    match command {
        "text" => {
            let (value, trailing) = parse_string_argument(arguments, line_number)?;
            if !trailing.is_empty() {
                return Err(ParseError::new(
                    line_number,
                    "unexpected arguments after text",
                ));
            }
            Ok(Component {
                id: None,
                type_name: "text".to_string(),
                value: Some(value),
                props: None,
            })
        }
        "button" => {
            let (value, trailing) = parse_string_argument(arguments, line_number)?;
            let action = if trailing.is_empty() {
                None
            } else if let Some(target) = trailing.strip_prefix("navigate ") {
                let (target, extra) = parse_string_argument(target, line_number)?;
                if !extra.is_empty() || target.trim().is_empty() {
                    return Err(ParseError::new(
                        line_number,
                        "navigation syntax is: button \"label\" navigate \"screen-id\"",
                    ));
                }
                Some(format!("navigate:{target}"))
            } else {
                return Err(ParseError::new(
                    line_number,
                    "expected 'navigate \"screen-id\"' after button label",
                ));
            };
            Ok(Component {
                id: None,
                type_name: "button".to_string(),
                value: Some(value),
                props: action.map(|action| ComponentProps {
                    action: Some(action),
                    ..ComponentProps::default()
                }),
            })
        }
        "input" => {
            let (id, remainder) = parse_string_argument(arguments, line_number)?;
            let (placeholder, trailing) = parse_string_argument(remainder, line_number)?;
            if !trailing.is_empty() || id.trim().is_empty() {
                return Err(ParseError::new(
                    line_number,
                    "input syntax is: input \"id\" \"placeholder\"",
                ));
            }
            Ok(Component {
                id: Some(id),
                type_name: "input".to_string(),
                value: None,
                props: Some(ComponentProps {
                    placeholder: Some(placeholder),
                    ..ComponentProps::default()
                }),
            })
        }
        "image" => {
            let (source, trailing) = parse_string_argument(arguments, line_number)?;
            if !trailing.is_empty() || source.trim().is_empty() {
                return Err(ParseError::new(
                    line_number,
                    "image syntax is: image \"url\"",
                ));
            }
            Ok(Component {
                id: None,
                type_name: "image".to_string(),
                value: Some(source),
                props: Some(ComponentProps::default()),
            })
        }
        "card" => {
            let (title, trailing) = parse_string_argument(arguments, line_number)?;
            if !trailing.is_empty() {
                return Err(ParseError::new(
                    line_number,
                    "unexpected arguments after card title",
                ));
            }
            Ok(Component {
                id: None,
                type_name: "card".to_string(),
                value: Some(title),
                props: Some(ComponentProps {
                    children: Some(Vec::new()),
                    ..ComponentProps::default()
                }),
            })
        }
        _ => unreachable!("component command was validated before parsing"),
    }
}

fn parse_container(
    command: &str,
    arguments: &str,
    line_number: usize,
) -> Result<Component, ParseError> {
    let (direction, scrollable) = match command {
        "row" => ("horizontal", false),
        "column" => ("vertical", false),
        "list" | "scroll" => {
            let direction = arguments.trim();
            if !matches!(direction, "horizontal" | "vertical") {
                return Err(ParseError::new(
                    line_number,
                    format!("{command} syntax is: {command} horizontal|vertical"),
                ));
            }
            (direction, true)
        }
        _ => unreachable!("container command was validated before parsing"),
    };
    if matches!(command, "row" | "column") && !arguments.is_empty() {
        return Err(ParseError::new(
            line_number,
            format!("{command} does not accept arguments"),
        ));
    }

    Ok(Component {
        id: None,
        type_name: "stack".to_string(),
        value: None,
        props: Some(ComponentProps {
            direction: Some(direction.to_string()),
            spacing: Some(8.0),
            scrollable: Some(scrollable),
            children: Some(Vec::new()),
            ..ComponentProps::default()
        }),
    })
}

fn apply_style(
    arguments: &str,
    line_number: usize,
    component: &mut Component,
) -> Result<(), ParseError> {
    let (property, remainder) = parse_string_argument(arguments, line_number)?;
    let (value, trailing) = parse_string_argument(remainder, line_number)?;
    if !trailing.is_empty() {
        return Err(ParseError::new(
            line_number,
            "style syntax is: style \"property\" \"value\"",
        ));
    }
    let props = component.props.get_or_insert_with(ComponentProps::default);
    match property.as_str() {
        "color" => props.color = Some(value),
        "background" | "backgroundColor" | "background-color" => {
            props.background_color = Some(value)
        }
        "size" | "fontSize" | "font-size" | "width" | "height" => {
            props.size = Some(parse_style_number(&value, &property, line_number)?)
        }
        "padding" => props.padding = Some(parse_style_number(&value, &property, line_number)?),
        "rounded" | "radius" | "borderRadius" | "border-radius" => {
            props.rounded = Some(parse_style_number(&value, &property, line_number)?)
        }
        "spacing" | "gap" => {
            props.spacing = Some(parse_style_number(&value, &property, line_number)?)
        }
        "alignment" | "align" | "textAlign" | "text-align" => props.alignment = Some(value),
        "direction" => props.direction = Some(value),
        "fontWeight" | "font-weight" => props.font_weight = Some(value),
        "scroll" => {
            props.scrollable = Some(match value.as_str() {
                "true" => true,
                "false" => false,
                _ => {
                    return Err(ParseError::new(
                        line_number,
                        "scroll style value must be 'true' or 'false'",
                    ))
                }
            })
        }
        _ => {
            return Err(ParseError::new(
                line_number,
                format!("unknown style property '{property}'"),
            ))
        }
    }
    Ok(())
}

fn parse_style_number(value: &str, property: &str, line_number: usize) -> Result<f32, ParseError> {
    let value = value.strip_suffix("px").unwrap_or(value);
    let number = value.parse::<f32>().map_err(|_| {
        ParseError::new(line_number, format!("style '{property}' must be a number"))
    })?;
    if !number.is_finite() || number < 0.0 {
        return Err(ParseError::new(
            line_number,
            format!("style '{property}' must be a non-negative number"),
        ));
    }
    Ok(number)
}

fn last_component_mut<'a>(
    open_containers: &'a mut [Component],
    current_route: &'a mut Option<OpenRoute>,
    root_components: &'a mut Vec<Component>,
) -> Option<&'a mut Component> {
    if let Some(container) = open_containers.last_mut() {
        let has_children = !container.props.as_ref()?.children.as_ref()?.is_empty();
        if has_children {
            return container.props.as_mut()?.children.as_mut()?.last_mut();
        }
        return Some(container);
    }
    if let Some(route) = current_route {
        route.components.last_mut()
    } else {
        root_components.last_mut()
    }
}

fn add_component(
    component: Component,
    open_containers: &mut [Component],
    current_route: &mut Option<OpenRoute>,
    root_components: &mut Vec<Component>,
) {
    if let Some(container) = open_containers.last_mut() {
        container
            .props
            .as_mut()
            .and_then(|props| props.children.as_mut())
            .expect("open cards always have a children list")
            .push(component);
    } else if let Some(route) = current_route {
        route.components.push(component);
    } else {
        root_components.push(component);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_escaped_quotes_and_command_words_in_values() {
        let app = parse(r#"text "The word text and \"quotes\"""#).unwrap();
        assert_eq!(app.screens[0].type_name, "text");
        assert_eq!(
            app.screens[0].value.as_deref(),
            Some("The word text and \"quotes\"")
        );
    }

    #[test]
    fn parses_cards_inputs_and_navigation_routes() {
        let app = parse(
            "screen \"home\" \"Inicio\"\ncard \"Perfil\"\ninput \"name\" \"Tu nombre\"\nend\nbutton \"Abrir\" navigate \"profile\"\nscreen \"profile\" \"Perfil\"\ntext \"Hola\"",
        )
        .unwrap();
        assert_eq!(app.initial_route.as_deref(), Some("home"));
        assert_eq!(app.routes.len(), 2);
        assert_eq!(app.routes[0].components[0].type_name, "card");
        assert_eq!(
            app.routes[0].components[0]
                .props
                .as_ref()
                .unwrap()
                .children
                .as_ref()
                .unwrap()[0]
                .type_name,
            "input"
        );
        assert_eq!(
            app.routes[0].components[1]
                .props
                .as_ref()
                .unwrap()
                .action
                .as_deref(),
            Some("navigate:profile")
        );
    }

    #[test]
    fn parses_images_lists_scroll_containers_and_styles() {
        let app = parse(
            "screen \"home\" \"Inicio\"\nlist horizontal\nimage \"https://example.com/a.png\"\nstyle \"size\" \"120\"\nend\nscroll vertical\ncard \"Perfil\"\nstyle \"background\" \"#fff\"\ntext \"Hola\"\nstyle \"color\" \"#123456\"\nstyle \"font-size\" \"24px\"\nend\nend\nscreen \"profile\" \"Perfil\"\nnavigation \"home\" \"Inicio\"",
        )
        .unwrap();
        let schema: serde_json::Value =
            serde_json::from_str(&crate::compiler::compile(app).unwrap()).unwrap();

        let horizontal_list = &schema["routes"][0]["components"][0];
        assert_eq!(horizontal_list["type"], "stack");
        assert_eq!(horizontal_list["props"]["direction"], "horizontal");
        assert_eq!(horizontal_list["props"]["scrollable"], true);
        assert_eq!(
            horizontal_list["props"]["children"][0]["value"],
            "https://example.com/a.png"
        );
        assert_eq!(
            horizontal_list["props"]["children"][0]["props"]["size"],
            120.0
        );

        let vertical_scroll = &schema["routes"][0]["components"][1];
        assert_eq!(vertical_scroll["props"]["direction"], "vertical");
        assert_eq!(vertical_scroll["props"]["scrollable"], true);
        assert_eq!(
            vertical_scroll["props"]["children"][0]["props"]["backgroundColor"],
            "#fff"
        );
        assert_eq!(
            vertical_scroll["props"]["children"][0]["props"]["children"][0]["props"]["color"],
            "#123456"
        );
        assert_eq!(
            vertical_scroll["props"]["children"][0]["props"]["children"][0]["props"]["size"],
            24.0
        );
        assert_eq!(schema["navigationBar"][0]["route"], "home");

        let layout = parse("row\ntext \"Uno\"\nend\ncolumn\ntext \"Dos\"\nend").unwrap();
        assert_eq!(
            layout.screens[0]
                .props
                .as_ref()
                .unwrap()
                .direction
                .as_deref(),
            Some("horizontal")
        );
        assert_eq!(
            layout.screens[1]
                .props
                .as_ref()
                .unwrap()
                .direction
                .as_deref(),
            Some("vertical")
        );
    }

    #[test]
    fn rejects_invalid_component_styles_with_a_line_number() {
        let error = parse("text \"Hola\"\nstyle \"padding\" \"-1\"").unwrap_err();
        assert_eq!(
            error.to_string(),
            "line 2: style 'padding' must be a non-negative number"
        );
    }

    #[test]
    fn rejects_navigation_to_an_unknown_screen() {
        let error = parse("button \"Abrir\" navigate \"missing\"").unwrap_err();
        assert!(error.to_string().contains("not a declared screen"));
    }

    #[test]
    fn parses_bottom_navigation_tabs_and_validates_destinations() {
        let app = parse(
            "screen \"home\" \"Inicio\"\ntext \"Hola\"\nscreen \"profile\" \"Perfil\"\ntext \"Yo\"\ntab \"home\" \"Inicio\"\ntab \"profile\" \"Perfil\"",
        )
        .unwrap();
        assert_eq!(app.navigation_bar.len(), 2);
        assert_eq!(app.navigation_bar[0].route, "home");
        assert_eq!(app.navigation_bar[1].label, "Perfil");

        let error = parse("screen \"home\"\ntab \"missing\" \"Falta\"").unwrap_err();
        assert!(error.to_string().contains("navigation tab target"));
    }

    #[test]
    fn rejects_unknown_components_instead_of_silently_ignoring_them() {
        let error = parse("textbox \"not a text component\"").unwrap_err();
        assert!(error.to_string().contains("unknown component"));
    }

    #[test]
    fn reports_the_line_of_malformed_input() {
        let error = parse("text \"valid\"\nbutton unquoted").unwrap_err();
        assert_eq!(error.to_string(), "line 2: expected a valid quoted string");
    }
}
