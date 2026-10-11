//! First weapon state integration. Uses imported original variant reload values.
//! Reserve supply and target ballistics remain explicit baseplate test rules.
use glam::Vec3;
use std::path::Path;
use std::collections::HashMap;

#[derive(Clone)]
pub(super) struct ShellProfile { pub clips: Vec<String>, start: f32, start_add: f32, end: f32 }
#[derive(Clone)]
pub(super) struct WeaponProfile {
    pub model: String,
    pub name: String,
    pub capacity: u32,
    pub loaded_ms: u32,
    pub empty_ms: u32,
    pub clips: Vec<String>,
    pub parts: Option<String>,
    pub magazine_asset: Option<String>,
    pub magazine_offset: Vec3,
    pub fire_test_seconds: f32,
    pub shells: Option<ShellProfile>,
    pub fire_mode: u8,
    pub fire_seconds: f32,
    pub burst_delay: f32,
    last_fire: f32, reload_add: f32, empty_add: f32,
}

pub(super) struct WeaponCatalog(pub HashMap<String, WeaponProfile>);
impl WeaponCatalog {
    pub(super) fn load(asset_root: &str) -> Self {
        let text = std::fs::read_to_string(Path::new(asset_root).join("local-bo2/weapons/weapon-catalog.txt"))
            .expect("Original weapon catalog missing; run build_weapon_catalog.py");
        let fire_text = std::fs::read_to_string(Path::new(asset_root).join("local-bo2/weapons/fire-catalog.txt")).expect("Original fire catalog missing");
        let fire: HashMap<_,_> = fire_text.lines().map(|line| {
            let f: Vec<_> = line.split('|').collect(); assert_eq!(f.len(),8);
            let mode: u8 = f[1].parse().unwrap(); let interval = (f[2].parse::<f32>().unwrap()+f[4].parse::<f32>().unwrap())/1000.; let burst = f[3].parse::<f32>().unwrap()/1000.;
            assert!(mode <= 5 && interval > 0. && interval <= 10. && burst >= 0.);
            (f[0].to_owned(),(mode,interval,burst,f[5].parse::<f32>().unwrap()/1000.,f[6].parse::<f32>().unwrap()/1000.,f[7].parse::<f32>().unwrap()/1000.))
        }).collect();
        let shell_text=std::fs::read_to_string(Path::new(asset_root).join("local-bo2/weapons/shell-catalog.txt")).expect("Shell catalog missing");
        let shells:HashMap<_,_>=shell_text.lines().filter(|l|!l.is_empty()).map(|line| {
            let f:Vec<_>=line.split('|').collect();assert_eq!(f.len(),5);
            let p=ShellProfile {clips:f[1].split(',').map(str::to_owned).collect(),start:f[2].parse::<f32>().unwrap()/1000.,start_add:f[3].parse::<f32>().unwrap()/1000.,end:f[4].parse::<f32>().unwrap()/1000.};
            assert!(p.clips.len()==3 && p.start>0. && p.end>0. && p.start_add<=p.start);(f[0].to_owned(),p)
        }).collect();
        let mut profiles = HashMap::new();
        for line in text.lines().filter(|line| !line.is_empty()) {
            let fields: Vec<_> = line.split('|').collect();
            assert_eq!(fields.len(), 10, "Invalid original weapon catalog row");
            let clips: Vec<_> = fields[5].split(',').map(str::to_owned).collect();
            assert_eq!(clips.len(), 7);
            let profile = WeaponProfile {
                shells: shells.get(fields[0]).cloned(),
                model: fields[0].into(), name: fields[1].into(), capacity: fields[2].parse().unwrap(),
                loaded_ms: fields[3].parse().unwrap(), empty_ms: fields[4].parse().unwrap(), clips,
                parts: (!fields[6].is_empty()).then(|| fields[6].to_owned()),
                magazine_asset: (!fields[8].is_empty()).then(|| fields[8].to_owned()),
                magazine_offset: {
                    let xyz: Vec<f32> = fields[9].split(',').map(|value| value.parse().unwrap()).collect();
                    assert_eq!(xyz.len(), 3); assert!(xyz.iter().all(|v| v.is_finite() && v.abs() < 128.));
                    Vec3::new(xyz[0], xyz[1], xyz[2])
                },
                fire_test_seconds: fields[7].parse::<f32>().unwrap() / 1000.,
                fire_mode: fire[fields[0]].0, fire_seconds: fire[fields[0]].1, burst_delay: fire[fields[0]].2,
                last_fire: fire[fields[0]].3, reload_add: fire[fields[0]].4, empty_add: fire[fields[0]].5,
            };
            let token = profile.model.strip_prefix("t6_wpn_").unwrap().strip_suffix("_view.glb").unwrap().split_once('_').unwrap().1;
            let family = match token {
                "fiveseven" => "fn57", "b2023r" => "beretta2023r", "type95" => "type95_ar",
                "saiga" => "saiga12", "mp5" => "mp5_t6", "python" => "python_sw", "fal" => "fal_t6",
                "browninghp" => "browning_hp", "spas" => "spas12_t6",
                "makarov" => "makarov_t6", "ak47" => "ak47_t6", "m60" => "m60_t6", "uzi" => "uzi_t6",
                "x95l" => "tavor", "chicom" => "qcw05", "galil" => "galil_t6", "ak74u" => "ak74u_t6",
                other => other,
            };
            let prefix = format!("viewmodel_{family}_");
            assert!(profile.clips.iter().all(|clip| clip.starts_with(&prefix)), "Gun {} was assigned another weapon family's animation", profile.model);
            assert!(profile.capacity > 0 && profile.capacity <= 1000 && profile.loaded_ms > 0 && profile.empty_ms > 0);
            profiles.insert(profile.model.clone(), profile);
        }
        assert!(profiles.contains_key("t6_wpn_pistol_m1911_view.glb"));
        Self(profiles)
    }
}


pub(super) struct WeaponState {
    pub model: String,
    pub name: String,
    fire_interval: f32,
    fire_mode: u8,
    burst_delay: f32,
    burst_remaining: u8,
    last_fire: f32, reload_add: f32, empty_add: f32,
    insert_remaining: f32, inserted: bool,
    shells: Option<ShellProfile>,
    pub reload_stage: u8, pub reload_cycle: u64, stop_reload: bool,
    saved_ammo: HashMap<String, (u32, u32)>,
    pub magazine: u32,
    pub reserve: u32,
    pub capacity: u32,
    pub reload_remaining: f32,
    reload_loaded: f32,
    reload_empty: f32,
    cooldown: f32,
    pub shots: u64,
    pub feedback: String,
}
impl WeaponState {
    pub fn load(asset_root: &str) -> Self {
        let text = std::fs::read_to_string(
            Path::new(asset_root).join("local-bo2/weapons/m1911-variant.txt"),
        )
        .expect("Original M1911 scalar profile missing; run original content import");
        let value = |name: &str| {
            text.lines()
                .find_map(|line| {
                    line.strip_prefix(&format!("{name}="))
                        .and_then(|v| v.parse::<u32>().ok())
                })
                .expect("Original M1911 scalar field missing")
        };
        let capacity = value("clip_size");
        assert!(capacity > 0 && capacity <= 1000);
        Self {
            model: "t6_wpn_pistol_m1911_view.glb".into(),
            name: "m1911_zm".into(),
            fire_interval: 0.096, fire_mode: 1, burst_delay: 0.2, burst_remaining: 0,
            last_fire: 0.24, reload_add: 1.05, empty_add: 1.1, insert_remaining: 0., inserted: false,
            shells: None, reload_stage: 0, reload_cycle: 0, stop_reload: false,
            saved_ammo: HashMap::new(),
            magazine: capacity,
            reserve: 40,
            capacity,
            reload_remaining: 0.,
            reload_loaded: value("reload_ms") as f32 / 1000.,
            reload_empty: value("reload_empty_ms") as f32 / 1000.,
            cooldown: 0.,
            shots: 0,
            feedback: "Ready | test reserve supply: 40".into(),
        }
    }
    pub fn fire(&mut self) -> bool {
        if self.reload_remaining > 0. {
            self.feedback = "Reloading".into();
            return false;
        }
        if self.cooldown > 0. {
            return false;
        }
        if self.magazine == 0 {
            self.feedback = "Empty | press R".into();
            return false;
        }
        self.magazine -= 1;
        self.shots += 1;
        // Base firing interval comes from the original inline Xbox WeaponDef.
        // Bolt-action lockout includes the original rechamber duration.
        // Specialized rotary/variable-rate controllers and blending remain pending.
        self.cooldown = if self.magazine == 0 && self.last_fire > 0. { self.last_fire } else { self.fire_interval };
        self.feedback = "Shot accepted | ballistics/effects pending".into();
        true
    }
    pub fn trigger(&mut self, held: bool, pressed: bool) -> bool {
        if self.reload_remaining > 0. { self.burst_remaining = 0;
            if self.shells.is_some() && pressed && self.magazine > 0 { self.stop_reload = true; }
            return false; }
        if self.fire_mode >= 2 && pressed && self.cooldown <= 0. && self.burst_remaining == 0 {
            self.burst_remaining = self.fire_mode; // Enum 2/3/4/5 is its burst round count.
        }
        let requested = if self.fire_mode == 0 { held || pressed } else if self.fire_mode == 1 { pressed } else { self.burst_remaining > 0 };
        if !requested || !self.fire() { if self.magazine == 0 { self.burst_remaining = 0; } return false; }
        if self.burst_remaining > 0 {
            self.burst_remaining -= 1;
            if self.burst_remaining == 0 { self.cooldown += self.burst_delay; }
        }
        true
    }
    pub(super) fn equip(&mut self, profile: &WeaponProfile) {
        if self.model == profile.model {
            self.name = profile.name.clone();
            self.capacity = profile.capacity;
            self.shells = profile.shells.clone();
            self.reload_loaded = profile.loaded_ms as f32 / 1000.;
            self.reload_empty = profile.empty_ms as f32 / 1000.;
            self.fire_interval = profile.fire_seconds;
            self.fire_mode = profile.fire_mode; self.burst_delay = profile.burst_delay;
            self.last_fire = profile.last_fire; self.reload_add = profile.reload_add; self.empty_add = profile.empty_add;
            return;
        }
        self.saved_ammo.insert(self.model.clone(), (self.magazine, self.reserve));
        self.model = profile.model.clone();
        self.name = profile.name.clone();
        self.capacity = profile.capacity;
        let ammo = self.saved_ammo.get(&self.model).copied().unwrap_or((self.capacity, 40));
        self.magazine = ammo.0.min(self.capacity); self.reserve = ammo.1;
        self.shells = profile.shells.clone();
            self.reload_loaded = profile.loaded_ms as f32 / 1000.;
        self.reload_empty = profile.empty_ms as f32 / 1000.;
        self.fire_interval = profile.fire_seconds;
        self.fire_mode = profile.fire_mode; self.burst_delay = profile.burst_delay;
            self.last_fire = profile.last_fire; self.reload_add = profile.reload_add; self.empty_add = profile.empty_add;
        self.reload_remaining = 0.; self.reload_stage = 0; self.stop_reload = false; self.cooldown = 0.; self.burst_remaining = 0; self.inserted = false; self.insert_remaining = 0.; self.feedback = "Equipped | original ammo/reload profile".into();
    }
    pub fn reload(&mut self) -> bool {
        if self.reload_remaining > 0. || self.magazine == self.capacity || self.reserve == 0 {
            return false;
        }
        self.burst_remaining = 0;
        if let Some(shells) = &self.shells {
            self.reload_stage=8; self.reload_cycle+=1; self.stop_reload=false;
            self.reload_remaining=shells.start; self.insert_remaining=shells.start_add;
            self.inserted=false; self.feedback="Reload start | individual shells".into();return true;
        }
        self.reload_remaining = if self.magazine == 0 {
            self.reload_empty
        } else {
            self.reload_loaded
        };
        let add = if self.magazine == 0 && self.empty_add > 0. { self.empty_add } else { self.reload_add };
        // Per-shell profiles (e.g. Python) need their own controller. Preserve
        // the existing explicit test transfer when their stage exceeds this clip.
        self.insert_remaining = if add > 0. && add <= self.reload_remaining { add } else { self.reload_remaining };
        self.inserted = false;
        self.feedback = "Reloading | original variant duration".into();
        true
    }
    pub(super) fn tick(&mut self, delta: f32) {
        self.cooldown = (self.cooldown - delta).max(0.);
        if self.shells.is_some() && self.reload_stage > 0 {
            let mut left=delta;
            while left>0. && self.reload_stage>0 {
                let step=left.min(self.reload_remaining);left-=step;
                self.reload_remaining=(self.reload_remaining-step).max(0.);
                self.insert_remaining=(self.insert_remaining-step).max(0.);
                if !self.inserted && self.reload_stage!=10 && self.insert_remaining==0. {
                    self.inserted=true;
                    if self.magazine<self.capacity && self.reserve>0 {self.magazine+=1;self.reserve-=1;}
                }
                if self.reload_remaining==0. {
                    if self.reload_stage==10 {self.reload_stage=0;self.feedback="Reload complete".into();break;}
                    if self.stop_reload || self.magazine==self.capacity || self.reserve==0 {
                        self.reload_stage=10;self.reload_remaining=self.shells.as_ref().unwrap().end;
                        self.insert_remaining=self.reload_remaining;self.inserted=true;
                    } else {
                        self.reload_stage=9;self.reload_remaining=self.reload_loaded;
                        self.insert_remaining=self.reload_add;self.inserted=false;
                        assert!(self.insert_remaining>0. && self.insert_remaining<=self.reload_remaining);
                    }
                    self.reload_cycle+=1;
                }
            }
            return;
        }
        if self.reload_remaining > 0. {
            self.reload_remaining = (self.reload_remaining - delta).max(0.);
            self.insert_remaining = (self.insert_remaining - delta).max(0.);
            if !self.inserted && self.insert_remaining == 0. {
                self.inserted = true;
                let amount = (self.capacity - self.magazine).min(self.reserve);
                self.magazine += amount;
                self.reserve -= amount;
                self.feedback = "Ammo inserted | finishing reload".into();
            }
            if self.reload_remaining == 0. { self.feedback = "Reload complete".into(); }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn state() -> WeaponState {
        WeaponState {
            model: "t6_wpn_pistol_m1911_view.glb".into(), name: "m1911_zm".into(),
            fire_interval: 0.096, fire_mode: 1, burst_delay: 0.2, burst_remaining: 0,
            last_fire: 0.24, reload_add: 1.05, empty_add: 1.1, insert_remaining: 0., inserted: false, shells: None, reload_stage: 0, reload_cycle: 0, stop_reload: false, saved_ammo: HashMap::new(),
            magazine: 8,
            reserve: 40,
            capacity: 8,
            reload_remaining: 0.,
            reload_loaded: 1.63,
            reload_empty: 1.8,
            cooldown: 0.,
            shots: 0,
            feedback: String::new(),
        }
    }
    #[test]
    fn automatic_short_click_survives_release_before_frame() {
        let mut w=state();w.fire_mode=0;
        assert!(w.trigger(false,true));assert_eq!(w.magazine,7);
        w.tick(1.);assert!(!w.trigger(false,false));assert_eq!(w.magazine,7);
    }
    #[test]
    fn rejects_empty_and_cooldown_without_losing_ammo() {
        let mut w = state();
        assert!(w.fire());
        assert!(!w.fire());
        assert_eq!((w.magazine, w.shots), (7, 1));
        for _ in 0..7 {
            w.tick(1.);
            assert!(w.fire());
        }
        w.tick(1.);
        assert!(!w.fire());
        assert_eq!((w.magazine, w.shots), (0, 8));
    }
    #[test]
    fn reload_is_timed_and_conserves_ammo() {
        let mut w = state();
        w.magazine = 3;
        assert!(w.reload());
        assert!(!w.fire());
        w.tick(1.04);
        assert_eq!((w.magazine, w.reserve), (3, 40));
        w.tick(0.60);
        assert_eq!((w.magazine, w.reserve), (8, 35));
        assert!(!w.reload());
    }
    #[test]
    fn empty_reload_uses_distinct_duration_and_limited_reserve() {
        let mut w = state();
        w.magazine = 0;
        w.reserve = 3;
        assert!(w.reload());
        w.tick(1.09);
        assert_eq!(w.magazine, 0);
        w.tick(0.72);
        assert_eq!((w.magazine, w.reserve), (3, 0));
        assert!(!w.reload());
    }
    #[test]
    fn every_catalog_gun_conserves_ammo_and_switching_preserves_magazines() {
        let catalog = WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        let mut w = state();
        for profile in catalog.0.values().filter(|p|p.shells.is_none()) {
            w.equip(profile);
            w.magazine = profile.capacity; w.reserve = 200; w.cooldown = 0.;
            assert!(w.fire(), "{} cannot fire", profile.name);
            assert!(!w.fire(), "{} bypassed cooldown", profile.name);
            assert!(w.reload()); assert!(!w.fire());
            w.tick(profile.loaded_ms as f32 / 1000. + 0.001);
            assert_eq!((w.magazine, w.reserve), (profile.capacity, 199), "{} loaded reload", profile.name);
            w.magazine = 0; assert!(w.reload());
            w.tick(profile.empty_ms as f32 / 1000. + 0.001);
            assert_eq!((w.magazine, w.reserve), (profile.capacity, 199 - profile.capacity), "{} empty reload", profile.name);
        }
        let first = &catalog.0["t6_wpn_ar_fal_view.glb"];
        let second = &catalog.0["t6_wpn_pistol_m1911_view.glb"];
        w.equip(first); w.magazine = 3; w.reserve = 17; assert!(w.reload());
        w.equip(second); assert_eq!(w.reload_remaining, 0.);
        w.equip(first); assert_eq!((w.magazine, w.reserve), (3, 17));
        assert_eq!(w.reload_remaining, 0.);
    }
    #[test]
    fn original_fire_modes_hold_repeat_and_complete_bursts() {
        let catalog = WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        for profile in catalog.0.values().filter(|p|p.shells.is_none()) {
            let mut w = state(); w.equip(profile); w.magazine = 200; w.capacity = 200;
            let start = w.shots;
            for frame in 0..800 { w.tick(0.001); w.trigger(true,frame == 0); }
            let count = (w.shots-start) as i32;
            match profile.fire_mode {
                0 => { let expected = (0.8/profile.fire_seconds).ceil() as i32;
                    assert!((count-expected).abs() <= 1,"{} automatic cadence: {} vs {}",profile.name,count,expected); }
                1 => assert_eq!(count,1,"{} repeated while trigger held",profile.name),
                burst => assert_eq!(count,burst as i32,"{} wrong burst count",profile.name),
            }
            w.tick(2.); let before = w.shots;
            assert!(w.trigger(true,true)); assert_eq!(w.shots,before+1);
            assert!(w.reload());
            for _ in 0..50 { w.tick(0.001); assert!(!w.trigger(true,false)); }
        }
        let p=&catalog.0["t6_wpn_ar_m16a2_view.glb"];
        let mut w=state(); w.equip(p); w.magazine=2;
        assert!(w.trigger(true,true));
        for _ in 0..400 { w.tick(0.001); w.trigger(false,false); }
        assert_eq!(w.magazine,0); assert_eq!(w.burst_remaining,0);
    }

    #[test]
    fn bolt_action_rejects_another_shot_until_original_rechamber_duration() {
        let catalog = WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        let p = &catalog.0["t6_wpn_sniper_dsr50_view.glb"];
        assert!((p.fire_seconds-1.2).abs() < 0.001);
        let mut w = state(); w.equip(p); assert!(w.trigger(true,true));
        w.tick(0.21); assert!(!w.trigger(true,true));
        w.tick(1.); assert!(w.trigger(true,true));
    }

    #[test]
    fn reload_inserts_at_original_time_once_and_switching_conserves_it() {
        let catalog=WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        let p=&catalog.0["t6_wpn_pistol_m1911_view.glb"];
        let mut w=state();w.equip(p);w.magazine=3;assert!(w.reload());
        w.tick(1.04);assert_eq!((w.magazine,w.reserve),(3,40));
        w.tick(0.02);assert_eq!((w.magazine,w.reserve),(8,35));
        assert!(w.reload_remaining>0. && !w.trigger(true,true));
        w.tick(0.6);assert_eq!((w.magazine,w.reserve),(8,35));
        w.magazine=0;assert!(w.reload());w.tick(1.09);assert_eq!(w.magazine,0);
        w.tick(0.02);assert_eq!((w.magazine,w.reserve),(8,27));
        w.equip(&catalog.0["t6_wpn_ar_m14_view.glb"]);w.equip(p);
        assert_eq!((w.magazine,w.reserve),(8,27));assert_eq!(w.reload_remaining,0.);
    }

    #[test]
    fn every_gun_inserts_once_without_allowing_early_fire() {
        let catalog=WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        for p in catalog.0.values().filter(|p|p.shells.is_none()) {
            for empty in [false,true] {
                let mut w=state();w.equip(p);w.magazine=if empty {0}else{p.capacity-1};w.reserve=200;
                let before=w.magazine;let total=before+w.reserve;assert!(w.reload());
                let duration=w.reload_remaining;
                let source=if empty && p.empty_add>0. {p.empty_add}else{p.reload_add};
                let insertion=if source>0. && source<=duration {source}else{duration};
                w.tick(insertion-0.001);assert_eq!(w.magazine,before,"{} early insertion",p.name);
                w.tick(0.002);assert_eq!(w.magazine,p.capacity,"{} insertion missing",p.name);
                assert_eq!(w.magazine+w.reserve,total,"{} ammo not conserved",p.name);
                if insertion+0.002<duration {assert!(!w.trigger(true,true),"{} fired during reload finish",p.name);}
                w.tick(duration+0.1);assert_eq!(w.magazine+w.reserve,total);assert_eq!(w.reload_remaining,0.);
            }
        }
    }
    #[test]
    fn last_round_uses_original_final_shot_delay() {
        let catalog=WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        for p in catalog.0.values().filter(|p|p.last_fire>0.) {
            let mut w=state();w.equip(p);w.magazine=1;assert!(w.fire());
            assert!((w.cooldown-p.last_fire).abs()<0.0001,"{} last-shot timing",p.name);
        }
    }

    #[test]
    fn ksg_loads_shells_at_stage_times_and_can_interrupt_without_losing_ammo() {
        let c=WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));let p=&c.0["t6_wpn_shotty_ksg_view.glb"];
        let mut w=state();w.equip(p);w.magazine=11;w.reserve=10;assert!(w.reload());
        w.tick(0.749);assert_eq!(w.magazine,11);w.tick(0.002);assert_eq!((w.magazine,w.reserve),(12,9));
        w.tick(0.05);assert_eq!(w.reload_stage,9);w.tick(0.301);assert_eq!((w.magazine,w.reserve),(13,8));
        assert!(!w.trigger(true,true));w.tick(0.27);assert_eq!(w.reload_stage,10);
        w.tick(0.7);assert_eq!(w.reload_stage,0);assert_eq!((w.magazine,w.reserve),(13,8));assert!(w.trigger(true,true));
        w.tick(2.);w.magazine=0;w.reserve=3;assert!(w.reload());w.tick(20.);
        assert_eq!((w.magazine,w.reserve,w.reload_stage),(3,0,0));
        w.magazine=0;w.reserve=20;assert!(w.reload());w.tick(20.);assert_eq!((w.magazine,w.reserve),(14,6));
    }

    #[test]
    fn shell_profiles_conserve_ammo_at_every_stage_with_small_or_large_ticks() {
        let c=WeaponCatalog::load(&std::env::var("BASEPLATE_ASSETS").expect("Set BASEPLATE_ASSETS to your locally imported test assets"));
        for p in c.0.values().filter(|p|p.shells.is_some()) {
            for step in [0.001,0.017,0.1,10.] {
                let mut w=state();w.equip(p);w.magazine=0;w.reserve=p.capacity+3;assert!(w.reload());
                let total=w.reserve;
                for _ in 0..20000 {
                    w.tick(step);assert_eq!(w.magazine+w.reserve,total,"{} shell conservation",p.name);
                    assert!(w.magazine<=p.capacity);
                    if w.reload_stage==0 {break;}
                    assert!(!w.fire(),"{} fired before reload finish",p.name);
                }
                assert_eq!((w.magazine,w.reserve,w.reload_stage),(p.capacity,3,0),"{} full staged reload",p.name);
            }
        }
    }

}
