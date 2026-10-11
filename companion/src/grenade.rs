//! Original M67 scalar timings, separate from Minecraft projectile rendering.
use serde_json::{Value,json};
use std::{collections::HashMap,path::Path};
pub(super) struct Grenades {pub spec:Value,actors:HashMap<String,Cook>}
#[derive(Clone)] struct Cook {elapsed:u32,pin:u32,fuse:u32,delay:u32,throw_at:Option<u32>,finished:bool}
impl Cook {
    fn advance(&mut self,step:u32,held:bool)->Value {
        if !self.finished && !held && self.throw_at.is_none(){self.throw_at=Some(self.elapsed.max(self.pin)+self.delay);}
        if !self.finished {self.elapsed=self.elapsed.saturating_add(step);}
        let deadline=self.pin+self.fuse;
        let explode=!self.finished && self.elapsed>=deadline;
        let spawn=!self.finished && !explode && self.throw_at.is_some_and(|at|self.elapsed>=at);
        if explode || spawn {self.finished=true;}
        let phase=if explode {"explode"} else if spawn {"throw"} else if self.finished {"complete"} else if self.elapsed<self.pin {"pullpin"} else if self.throw_at.is_some() {"throw"} else {"hold"};
        let throw_start=self.throw_at.map(|at|at.saturating_sub(self.delay));
        json!({"active":!self.finished,"phase":phase,"elapsed_ms":self.elapsed,
            "phase_ms":if phase=="throw" {self.elapsed.saturating_sub(throw_start.unwrap_or(self.elapsed))}else{self.elapsed},
            "remaining_ms":deadline.saturating_sub(self.elapsed).min(self.fuse),"spawn":spawn,"explode":explode})
    }
}
impl Grenades {
    pub fn clear(&mut self){self.actors.clear();}
    pub fn cancel(&mut self,id:&str){self.actors.remove(id);}
    pub fn load(root:&str)->Self {
        let spec:Value=serde_json::from_str(&std::fs::read_to_string(Path::new(root).join("local-bo2/weapons/grenade-spec.json")).expect("Original grenade profile missing")).unwrap();
        assert_eq!(spec["name"],"frag_grenade_mp");
        for field in ["fuse_ms","pin_pull_ms","throw_delay_ms","throw_animation_ms","speed_units","blast_radius_units"] {assert!(spec[field].as_u64().is_some_and(|v|v>0 && v<120000));}
        assert_eq!(spec["cookable"],true);Self{spec,actors:HashMap::new()}
    }
    pub fn request(&mut self,id:&str,operation:&str,request:&Value)->Value {
        let Some(step)=request["step_ms"].as_u64().filter(|v|*v<=1000) else{return json!({"ok":false,"error":"Invalid grenade time step"});};
        if operation=="grenade_begin" {
            if self.actors.get(id).is_some_and(|c|!c.finished){return json!({"ok":false,"error":"A grenade is already being cooked"});}
            if self.actors.len()>=128 && !self.actors.contains_key(id){return json!({"ok":false,"error":"Grenade player limit"});}
            self.actors.insert(id.into(),Cook{elapsed:0,pin:self.spec["pin_pull_ms"].as_u64().unwrap() as u32,fuse:self.spec["fuse_ms"].as_u64().unwrap() as u32,delay:self.spec["throw_delay_ms"].as_u64().unwrap() as u32,throw_at:None,finished:false});
        } else if operation!="grenade_frame" {return json!({"ok":false,"error":"Unsupported grenade operation"});}
        let Some(actor)=self.actors.get_mut(id) else{return json!({"ok":false,"error":"No active grenade"});};
        let state=actor.advance(step as u32,request["held"].as_bool().unwrap_or(false));
        json!({"ok":true,"protocol":1,"sequence":request["sequence"],"grenade":state,"grenade_spec":self.spec})
    }
}
#[cfg(test)] mod tests {
    use super::*;
    fn cook()->Cook{Cook{elapsed:0,pin:550,fuse:3500,delay:120,throw_at:None,finished:false}}
    #[test]fn quick_release_finishes_pin_pull_then_delays_throw(){
        let mut c=cook();assert_eq!(c.advance(0,false)["phase"],"pullpin");
        assert_eq!(c.advance(650,true)["spawn"],false);
        let state=c.advance(20,true);assert_eq!(state["spawn"],true);assert_eq!(state["remaining_ms"],3380);
        assert_eq!(c.advance(50,false)["spawn"],false);
    }
    #[test]fn cooking_reduces_thrown_fuse_and_never_restarts_it(){
        let mut c=cook();c.advance(2550,true);c.advance(0,false);
        let state=c.advance(120,false);assert_eq!(state["spawn"],true);assert_eq!(state["remaining_ms"],1380);
        assert_eq!(c.advance(120,false)["spawn"],false);
    }
    #[test]fn holding_past_original_fuse_explodes_in_hand_once(){
        let mut c=cook();assert_eq!(c.advance(4049,true)["explode"],false);
        assert_eq!(c.advance(1,true)["explode"],true);assert_eq!(c.advance(100,true)["explode"],false);
    }
    #[test]fn pause_does_not_advance_a_cooked_grenade(){
        let mut c=cook();let first=c.advance(1000,true);assert_eq!(first,c.advance(0,true));
    }
}
