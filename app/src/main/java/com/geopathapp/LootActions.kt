package com.geopathapp

import com.google.firebase.database.*
import java.util.*
import com.google.firebase.database.DataSnapshot

import com.google.firebase.database.DatabaseError

import com.google.firebase.database.MutableData

class LootActions {
    private lateinit var db : FirebaseDatabase
    private lateinit var invref: DatabaseReference
    private lateinit var userDataRef: DatabaseReference

    // converts an integer item ID to a text value for a given loot item, so it
    // can be stored as such in the database
    //
    // returns a String
    fun translateID(id : Int): String {
        when (id) {
            1 -> return("Treasure chest")
            2 -> return("Diamond")
            3 -> return("Ruby")
            4 -> return("Emerald")
            5 -> return("Gold Coin")
            6 -> return("Ring")
            7 -> return("Red Potion")
            8 -> return("Green Potion")
        }
        return ("Error")
    }

    // converts an integer item ID to a reference to a drawable so that
    // it can be displayed in the game's inventory screen
    //
    // returns an Int; reference to a resource
    fun getImgFromId(id: Int): Int {
        when (id) {
        //<a href="https://www.flaticon.com/free-icons/chest" title="chest icons">Chest icons created by Smashicons - Flaticon</a>
            1 -> return(R.drawable.treasure)
        // <a href="https://www.flaticon.com/free-icons/gem" title="gem icons">Gem icons created by Freepik - Flaticon</a>
            2 -> return(R.drawable.diamond)
        //<a href="https://www.flaticon.com/free-icons/ruby" title="ruby icons">Ruby icons created by Freepik - Flaticon</a>
            3 -> return(R.drawable.ruby)
        //<a href="https://www.flaticon.com/free-icons/gem" title="gem icons">Gem icons created by Freepik - Flaticon</a>
            4 -> return(R.drawable.gem)
        //<a href="https://www.flaticon.com/free-icons/coin" title="coin icons">Coin icons created by Freepik - Flaticon</a>
            5 -> return(R.drawable.coin)
        //<a href="https://www.flaticon.com/free-icons/ring" title="ring icons">Ring icons created by Freepik - Flaticon</a>
            6 -> return(R.drawable.ring)
        //<a href="https://www.flaticon.com/free-icons/potion" title="potion icons">Potion icons created by Freepik - Flaticon</a>
            7 -> return(R.drawable.potion)
        //<a href="https://www.flaticon.com/free-icons/potion" title="potion icons">Potion icons created by Freepik - Flaticon</a>
            8 -> return(R.drawable.potion2)
        }
        return (R.drawable.temp)
    }

    // generates a random number that dictates what loot the user will get
    // just generates and returns an integer, which can then be passed to
    // translateID. this is due to the way the game handles item quantity
    //
    // returns an Int
    fun generateLoot(): Int {
        return (2..8).random()
    }

    // generates a random number that decides whether the user gets a
    // treasure chest item or not
    // takes a uid because it is added to the player's inventory immediately
    // using generateReward(uid, itemId)
    //
    // returns a Boolean; true if player receives a treasure chest
    fun generateChest(uid: String?): Boolean{
        val rnd = (1..10).random()
        if (rnd > 7) {
            generateReward(uid, 1)
            return true
        }

        return false
    }

    // remove a treasure chest from the user's inventory when they create a route
    //
    // returns a Boolean; true if operation completed OK --- used to do this, now does not return
    fun removeChest(uid: String?) {
        if (uid != null) {
            db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
            invref = db.getReference("inventory/".plus(uid))

            invref.runTransaction(object: Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    for (itemSnapshot in currentData.children){
                        val item = itemSnapshot.getValue(GameItem::class.java)

                        if (item != null) {
                            if (item.gameItemId == 1) {
                                val currentQuant = item.gameItemQuant

                                item.gameItemUID?.let {
                                    if (currentQuant != null) {
                                        currentData.child(it).child("gameItemQuant").value = currentQuant - 1
                                    }
                                }
                            }
                        }
                    }
                    return (Transaction.success(currentData))
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {

                }
            })

        }
    }

    //this should update the player's score by specified amount
    fun updateScore(uid: String?, amount: Int) {
        db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
        userDataRef = db.getReference("userdata/".plus(uid))

        userDataRef.runTransaction(object: Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val user = currentData.getValue(UserData::class.java)
                val score = user?.score

                if (score != null) {
                    currentData.child("score").value = score + amount
                }

                return (Transaction.success(currentData))
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {

            }
        })
    }

    // this function is intended to place a given item into the user's
    // inventory. this is done either by changing the quantity, or
    // by creating a new database entry
    //
    // returns a Boolean; true if operation completed OK --- this is what i intended, but now does not return
    fun generateReward(uid: String?, lootId: Int) {
        if (uid != null) {
            db = FirebaseDatabase.getInstance("https://geopathapp-default-rtdb.europe-west1.firebasedatabase.app/")
            invref = db.getReference("inventory/".plus(uid))

            invref.runTransaction(object: Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    var foundItem = false
                    for (itemSnapshot in currentData.children){
                        val item = itemSnapshot.getValue(GameItem::class.java)

                        if (item != null) {
                            if (item.gameItemId == lootId) {
                                foundItem = true

                                val currentQuant = item.gameItemQuant

                                item.gameItemUID?.let {
                                    if (currentQuant != null) {
                                        currentData.child(it).child("gameItemQuant").value = currentQuant + 1
                                    }
                                }
                            }
                        }
                    }

                    if (!foundItem){
                        val lootUUID = UUID.randomUUID().toString()
                        val myLoot = GameItem(lootUUID, lootId, translateID(lootId), 1)

                        currentData.child(lootUUID).value = myLoot
                    }

                    return (Transaction.success(currentData))
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {

                }
            })

        }

    }
}
