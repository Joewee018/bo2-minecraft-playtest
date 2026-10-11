//! Local beta transport for the same weapon controller used by the baseplate.
use crate::weapon_state::{WeaponCatalog,WeaponState};
use serde_json::{Value,json};
use std::{collections::HashMap,io::{BufRead,BufReader,Write,Read},net::TcpListener,time::{Instant,Duration}};
struct Player { weapon: WeaponState, last: Instant }
pub(super) fn run() {
    let root=std::env::var("BASEPLATE_ASSETS").unwrap_or_else(|_|"assets".into());
    let token=std::env::var("BO2_BETA_TOKEN").expect("BO2_BETA_TOKEN required for beta transport");
    assert!(token.len()>=32 && token.len()<=128);
    let catalog=WeaponCatalog::load(&root);let mut players:HashMap<String,Player>=HashMap::new();
    let mut grenades=crate::grenade::Grenades::load(&root);
    let port=std::env::var("BO2_BETA_PORT").map(|v|v.parse::<u16>().expect("Invalid BO2_BETA_PORT")).unwrap_or(28762);
    assert!(port!=0,"BO2_BETA_PORT must be nonzero");
    let listener=TcpListener::bind((std::net::Ipv4Addr::LOCALHOST,port)).expect("Beta weapon bridge port unavailable");
    println!("BO2 beta weapon bridge listening on loopback; {} connected profiles",catalog.0.len());
    for connection in listener.incoming() {
        let Ok(mut stream)=connection else{continue;};
        let _=stream.set_read_timeout(Some(Duration::from_secs(2)));
        let _=stream.set_write_timeout(Some(Duration::from_secs(2)));
        let mut line=String::new();
        let read=BufReader::new(&mut stream).take(8192).read_line(&mut line);
        let response=if read.is_err() || !line.ends_with('\n') {json!({"ok":false,"error":"Invalid bounded request"})}
        else {match serde_json::from_str::<Value>(&line) {
            Ok(request) if request["token"].as_str()==Some(token.as_str()) && request["protocol"]==1 => {
                let op=request["op"].as_str().unwrap_or("");
                if op=="ping" {json!({"ok":true,"protocol":1,"guns":catalog.0.len()})}
                else if op=="player_reset" {
                    let id=request["player"].as_str().unwrap_or("");
                    if !valid_player(id){json!({"ok":false,"error":"Invalid player"})}
                    else {players.remove(id);grenades.cancel(id);json!({"ok":true,"protocol":1})}
                }
                else if op=="grenade_spec" {json!({"ok":true,"protocol":1,"grenade_spec":grenades.spec})}
                else if op=="grenade_reset" {grenades.clear();json!({"ok":true,"protocol":1})}
                else if op=="grenade_cancel" {
                    let id=request["player"].as_str().unwrap_or("");
                    if !valid_player(id){json!({"ok":false,"error":"Invalid grenade player"})}
                    else {grenades.cancel(id);json!({"ok":true,"protocol":1})}
                }
                else if op=="grenade_begin" || op=="grenade_frame" {
                    let id=request["player"].as_str().unwrap_or("");
                    if !valid_player(id){json!({"ok":false,"error":"Invalid grenade player"})}
                    else {grenades.request(id,op,&request)}
                }
                else if !matches!(op,"frame"|"reload"|"equip") {
                    json!({"ok":false,"error":"Unsupported operation"})
                } else {
                    let id=request["player"].as_str().unwrap_or("");
                    let model=request["weapon"].as_str().unwrap_or("");
                    if !valid_player(id) || !catalog.0.contains_key(model) || (!players.contains_key(id) && players.len()>=128) {
                        json!({"ok":false,"error":"Unsupported player or weapon"})
                    } else {
                        let profile=&catalog.0[model];
                        let player=players.entry(id.into()).or_insert_with(||Player {weapon:WeaponState::load(&root),last:Instant::now()});
                        let w=&mut player.weapon;w.equip(profile);
                        // Server simulation steps make paused/lagged Minecraft cadence deterministic.
                        let delta=if let Some(ms)=request["weapon_step_ms"].as_u64() { (ms.min(250) as f32)/1000. }
                            else {player.last.elapsed().as_secs_f32().min(0.15)};player.last=Instant::now();
                        let before=w.shots;
                        let held=request["held"].as_bool().unwrap_or(false);let pressed=request["pressed"].as_bool().unwrap_or(false);
                        let infinite=request["infinite_ammo"].as_bool().unwrap_or(false);
                        let steps=(delta/0.01).ceil().max(1.) as u32;
                        for step in 0..steps {
                            w.tick(delta/steps as f32);
                            if infinite && w.reload_remaining==0. {w.magazine=w.capacity;}
                            let bot=request["bot_fire"].as_bool();
                            if op=="frame" && bot.unwrap_or(true) && (bot.is_none() || w.shots==before) {w.trigger(held,pressed && step==0);}
                            if infinite && w.reload_remaining==0. {w.magazine=w.capacity;}
                        }
                        if op=="reload" {w.reload();}
                        json!({"ok":true,"protocol":1,"weapon":model,"name":w.name,"loaded":w.magazine,"reserve":w.reserve,
                            "capacity":w.capacity,"shots":w.shots-before,"total_shots":w.shots,"reload":w.reload_remaining,"stage":w.reload_stage,"reload_cycle":w.reload_cycle,
                            "feedback":w.feedback,"infinite_ammo":infinite,"sequence":request["sequence"]})
                    }
                }
            }
            _=>json!({"ok":false,"error":"Invalid protocol or session"})
        }};
        let _=writeln!(stream,"{response}");
        players.retain(|_,p|p.last.elapsed()<Duration::from_secs(3600));
    }
}
fn valid_player(id:&str)->bool {id.len()==36 && id.bytes().enumerate().all(|(i,b)|if [8,13,18,23].contains(&i){b==b'-'}else{b.is_ascii_hexdigit()})}
#[cfg(test)] mod tests {use super::*;#[test]fn bounded_player_ids(){assert!(valid_player("12345678-1234-1234-1234-123456789abc"));assert!(!valid_player("../player"));assert!(!valid_player("12345678_1234-1234-1234-123456789abc"));}}
