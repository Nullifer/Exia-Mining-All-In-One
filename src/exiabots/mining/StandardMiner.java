package exiabots.mining;

import java.awt.Point;
import java.util.ArrayList;

import com.runemate.game.api.hybrid.Environment;
import com.runemate.game.api.hybrid.entities.GameObject;
import com.runemate.game.api.hybrid.entities.LocatableEntity;
import com.runemate.game.api.hybrid.entities.details.Interactable;
import com.runemate.game.api.hybrid.input.Mouse;
import com.runemate.game.api.hybrid.local.Camera;
import com.runemate.game.api.hybrid.local.hud.InteractablePoint;
import com.runemate.game.api.hybrid.local.hud.interfaces.Inventory;
import com.runemate.game.api.hybrid.location.Coordinate;
import com.runemate.game.api.hybrid.location.navigation.Traversal;
import com.runemate.game.api.hybrid.location.navigation.basic.BresenhamPath;
import com.runemate.game.api.hybrid.region.Players;
import com.runemate.game.api.hybrid.util.calculations.Random;

import exiabots.ExiaMinerAIO;
import exiabots.mining.locations.Location;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

public class StandardMiner extends MiningStyle{	
	int notMiningCount = 0;
	private int urnAmount = 1;
	Location location;

	private boolean usePorters = false;
	private boolean useUrns = false;
	private boolean walkToBank = false;

	@Override
	public String getLocationName() {
		return location == null ? "Unknown" : location.getName();
	}

	@Override
	public Coordinate[] getRockLocations(){
		return location.getRocks();
	}

	@Override
	public Rock getOre() {
		return location == null || location.getOre() == null ? Rock.UNKNOWN : location.getOre();
	}

	@Override
	public void onStart(String... args) {
		ExiaMinerAIO.instance.getEventDispatcher().addListener(Paint.profitCounter);
		rockWatcher = new RockWatcher((GameObject rock) -> location.validate(rock), location.getRocks());
		rockWatcher.start();
		content = null;
	}

	@Override
	public void onStop() {
		if(rockWatcher != null){
			try{
				rockWatcher.interrupt();
			}catch(NullPointerException e){}
		}
	}

	@Override
	public void loop() {
		if(useUrns){
			ItemHandlers.manageUrns(urnAmount);
		}

		if(usePorters){
			usePorters = ItemHandlers.managePorters();
		}	

		if(location.shouldBank() || ItemHandlers.shouldBank(usePorters, location)){
			if(location.inBank()){
				if(location.isBankOpen()){
					Paint.status = "Depositing items";
					location.deposit();
				}else{
					Paint.status = "Opening bank";
					location.openBank();
				}
			}else{
				Paint.status = "Walking to bank";
				if(walkToBank){
					if(Traversal.isRunEnabled()){
						Traversal.toggleRun();
					}
				}
				location.walkToBank(walkToBank);
			}
		}else{
			if(location.inMine()){
				mine();
				if(Players.getLocal() != null){
					if(Players.getLocal().getAnimationId() == -1)notMiningCount++;
				}
				else notMiningCount = 0;

				if(notMiningCount >= 10){
					notMiningCount = 0;
					currentRock = null;
				}
			}else {
				if(location.isBankOpen()){
					Paint.status = "Closing bank";
					location.closeBank();
				}else{
					Paint.status = "Walking to mine";
					location.walkToMine();
				}
			}
		}
	}

	protected void mine() {
		if(currentRock == null || !currentRock.isValid()){
			currentRock = null;

			//Get a new rock
			LocatableEntity rock = location.getNextRock(currentRock);
			if(rock != null){
				if(rock.distanceTo(Players.getLocal()) > 8){
					Paint.status = "Walking to rock";
					walkTo(rock);
				}else{
					turnAndClick(rock);
				}
			}else{
				if(outOfRegion()){
					BresenhamPath.buildTo(location.getRocks()[0]).step();
				}else{
					Paint.status = "Preparing for respawn";
					//if there are no new rocks to get, walk to the next spawning rock
					walkToNextEmpty();
					Paint.status = "Waiting for respawn";
				}
			}
		}else{
			Paint.status = "Mining";
			if(currentRock != null && currentRock.getVisibility() < 80){
				Camera.concurrentlyTurnTo((int)(Camera.getYaw() + Random.nextInt(0, 360)) % 360);
			}
			hoverNext();
		}

	}

	public static Interactable next;
	private void hoverNext(){
		if(Inventory.getUsedSlots() == 27 && currentRock != null){
			next = location.firstStepToBank();
		}else{
			next = location.getNextRock(currentRock);
		}

		if(next == null){
			RockWatcher.Pair<Coordinate, Long, GameObject> pair = rockWatcher.nextRock();
			if(pair != null){
				next = pair.object;
			}
		}

		if(next != null){
			if(next.contains(Mouse.getPosition())){
				ReflexAgent.delay();
				InteractablePoint pt = next.getInteractionPoint();
				if(pt != null && pt.x >= 0 &&  pt.y >= 0){
					pt.x += Random.nextInt(-2,3);
					pt.y += Random.nextInt(-2,3);
					Mouse.move(pt);
				}else{
					next.hover();
				}
			}else{
				if(next instanceof GameObject && ((GameObject) next).getVisibility() < 80){
					Camera.concurrentlyTurnTo((Camera.getYaw() + Random.nextInt(0, 360)) % 360);
				}else{
					if(Random.nextInt(0,100) < 5){
						InteractablePoint pt = next.getInteractionPoint(new Point(Random.nextInt(-2,3), Random.nextInt(-2,3)));
						if(pt != null){
							Mouse.move(pt);
						}else{
							next.hover();
						}
					}
					ReflexAgent.delay();
				}
			}
		}
	}

	private GridPane content = null;
	CheckBox urnBox = new CheckBox("Use urns: ");
	TextField urnText = new TextField("" + urnAmount);
	CheckBox porterBox= new CheckBox("Use porters");
	CheckBox walkBox = new CheckBox("Walk when heavy");

	@Override 
	public GridPane getContentPane(final Button startButton) {
		if(content != null)return content;
		content = new GridPane();
		content.setPadding(new Insets(0,3,25,3));
		content.setHgap(1.0);
		content.setVgap(1.0);

		ListView<String> locationList = new ListView<String>(); 
		locationList.setPrefWidth(167);

		ListView<String> oreList = new ListView<String>(); 
		oreList.setPrefWidth(167);

		FlowPane settings = new FlowPane();
		settings.setPrefWrapLength(165);

		ObservableList<String> items = FXCollections.observableArrayList();

		ArrayList<Location> locations = getLocations();
		for (int i = 0; i < locations.size(); i++) {
			items.add(locations.get(i).getName());
		}
		locationList.setItems(items);

		locationList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				int index = locationList.getSelectionModel().getSelectedIndex();
				Location loc = locations.get(index);
				location = loc;
				ObservableList<String> items = FXCollections.observableArrayList();

				for(String ore: loc.getOres()){
					items.add(ore);
				}
				oreList.setItems(items);
				oreList.getSelectionModel().clearSelection();

				populateOptions(settings);
			}
		});

		oreList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if(newValue != null){
					location.intialize(newValue);
					startButton.setDisable(false);

					populateOptions(settings);
				}
			}
		});

		Label locLabel = new Label("Locations");
		locLabel.setAlignment(Pos.CENTER);
		locLabel.setPrefWidth(167);

		Label oreLabel = new Label("Ores");
		oreLabel.setAlignment(Pos.CENTER);
		oreLabel.setPrefWidth(167);

		Label setLabel = new Label("Settings");
		setLabel.setAlignment(Pos.CENTER);
		setLabel.setPrefWidth(167);

		content.add(locLabel, 0, 0);
		content.add(locationList, 0, 1); 

		content.add(oreLabel, 1, 0);
		content.add(oreList, 1, 1);

		content.add(setLabel, 2, 0);
		content.add(settings, 2, 1);

		return content;
	}

	private void  populateOptions(FlowPane settings){
		Node[] nodes = location.getSettingsNodes();
		settings.getChildren().clear();

		for (int i = 0; i < nodes.length; i++) {
			settings.getChildren().add(nodes[i]);
		}

		if(ExiaMinerAIO.isRS3){
			porterBox.setPadding(new Insets(10,50,0,5));
			settings.getChildren().add(porterBox);

			urnBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					urnText.setDisable(!newValue);
				}
			});

			urnBox.setPadding(new Insets(10,5,0,5));
			settings.getChildren().add(urnBox);

			urnText.setDisable(!urnBox.isSelected());
			urnText.setMaxWidth(35.0f);
			urnText.setPadding(new Insets(3,5,2,5));
			settings.getChildren().add(urnText);

		}

		walkBox.setPadding(new Insets(10,10,0,5));
		settings.getChildren().add(walkBox);
	}

	@Override
	public void loadSettings() {
		location.loadSettings();
		if(ExiaMinerAIO.isRS3){
			useUrns = urnBox.isSelected();
			usePorters = porterBox.isSelected();

			if(useUrns){
				location.depositBlackList.add("mining urn");
				try{
					urnAmount = Integer.parseInt(urnText.getText());
				}catch(NumberFormatException e){}
			}

			if(usePorters){
				location.depositBlackList.add("porter");
			}
		}
		walkToBank = walkBox.isSelected();
	}

	public void removeNodeFromGrid(final int row, final int column, GridPane gridPane) {
		ObservableList<Node> children = gridPane.getChildren();
		for(int i = 0; i < children.size(); i++) {
			if(GridPane.getRowIndex(children.get(i)) == row && GridPane.getColumnIndex(children.get(i)) == column) {
				children.remove(i);
				break;
			}
		}
	}

	private ArrayList<Location> getLocations() {
		ArrayList<Location> locations = new ArrayList<Location>();
		if(ExiaMinerAIO.isRS3){
			locations.add(new exiabots.mining.locations.rs3.AlKharid());
			locations.add(new exiabots.mining.locations.rs3.BarbarianVillage());
			if(Environment.isSDK())locations.add(new exiabots.mining.locations.rs3.CoalTrucks());
			if(Environment.isSDK())locations.add(new exiabots.mining.locations.rs3.DesertQuarry());
			locations.add(new exiabots.mining.locations.rs3.DwarvenMine());
			locations.add(new exiabots.mining.locations.rs3.DwarvenResourceMine());
			locations.add(new exiabots.mining.locations.rs3.LegendsGuild());
			locations.add(new exiabots.mining.locations.rs3.LivingRockCavern(this));
			locations.add(new exiabots.mining.locations.rs3.LumbridgeEast());
			locations.add(new exiabots.mining.locations.rs3.LumbridgeWest());
			locations.add(new exiabots.mining.locations.rs3.MiningGuild(rockWatcher, this));
			locations.add(new exiabots.mining.locations.rs3.Monastery());
			if(Environment.isSDK())locations.add(new exiabots.mining.locations.rs3.PiratesHideout());
			locations.add(new exiabots.mining.locations.rs3.Rimmington());
			locations.add(new exiabots.mining.locations.rs3.ShiloVillage());
			locations.add(new exiabots.mining.locations.rs3.VarrockEast());
			locations.add(new exiabots.mining.locations.rs3.VarrockWest());
			locations.add(new exiabots.mining.locations.rs3.Yanille());
		}else{
			locations.add(new exiabots.mining.locations.osrs.AlKharid());
			locations.add(new exiabots.mining.locations.osrs.BarbarianVillage());
			if(Environment.isSDK())locations.add(new exiabots.mining.locations.osrs.CoalTrucks());
			locations.add(new exiabots.mining.locations.osrs.LegendsGuild());
			locations.add(new exiabots.mining.locations.osrs.LumbridgeEast());
			locations.add(new exiabots.mining.locations.osrs.LumbridgeWest());
			locations.add(new exiabots.mining.locations.osrs.MiningGuild(rockWatcher, this));
			locations.add(new exiabots.mining.locations.osrs.Monastery());
			if(Environment.isSDK())locations.add(new exiabots.mining.locations.osrs.PiratesHideout());
			locations.add(new exiabots.mining.locations.osrs.Rimmington());
			locations.add(new exiabots.mining.locations.osrs.VarrockEast());
			locations.add(new exiabots.mining.locations.osrs.VarrockWest());
			locations.add(new exiabots.mining.locations.osrs.Yanille());
		}
		return locations;
	}
}
