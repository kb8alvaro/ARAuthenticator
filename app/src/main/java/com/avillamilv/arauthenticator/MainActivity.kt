package com.avillamilv.arauthenticator

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var TAG = "MyActivity"

    private lateinit var arFragment: ArFragment

    private var tileRenderable: ModelRenderable? = null
    private var carRenderable: ModelRenderable? = null
    private var foxRenderable: ModelRenderable? = null
    private var shoeRenderable: ModelRenderable? = null

    private var grid = Array(ROW_NUM) { arrayOfNulls<TranslatableNode>(COL_NUM) }
    private var deployed = false

    private var carNumber: Int = 0
    private var foxNumber: Int = 0
    private var shoeNumber: Int = 0

    companion object {
        // Defining grid size
        const val COL_NUM = 3
        const val ROW_NUM = 3

        // Defining maximum number of objects
        const val MAX_CAR_NUM = 1
        const val MAX_FOX_NUM = 1
        const val MAX_SHOE_NUM = 1
        const val MAX_OBJECT_NUM = MAX_CAR_NUM + MAX_FOX_NUM + MAX_SHOE_NUM

        // Defining correct positions
        const val CAR_ROW_CORRECT = 0
        const val CAR_COL_CORRECT = 1
        const val FOX_ROW_CORRECT = 1
        const val FOX_COL_CORRECT = 0
        const val SHOE_ROW_CORRECT = 2
        const val SHOE_COL_CORRECT = 2

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = sceneform_fragment as ArFragment

        initModels()

        // Adds a listener to the ARSceneView called before processing each frame
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            arFragment.onUpdate(frameTime)
            showButton(isGridFull())
        }

        // Adding button to check password
        buttonCheck.setOnClickListener {
            checkPassword()
        }
        showButton(false)

        arFragment.setOnTapArPlaneListener { hitResult: HitResult, _: Plane, _: MotionEvent ->
            if (deployed) {
                // If grid is set do not put another one
                return@setOnTapArPlaneListener
            }

            val spacing = 0.1F

            val anchorNode = AnchorNode(hitResult.createAnchor())
            anchorNode.setParent(arFragment.arSceneView.scene)

            // Add N ground tiles to the plane (N = COL x ROW)
            grid.matrixIndices { col, row ->
                val renderableModel = tileRenderable?.makeCopy() ?: return@matrixIndices
                TranslatableNode().apply {
                    this.state = TileCombination.EMPTY
                    setParent(anchorNode)
                    renderable = renderableModel
                    addOffset(x = row * spacing - spacing, z = col * spacing - spacing)
                    grid[col][row] = this

                    // When touched, the node iterates between models
                    this.setOnTapListener { _, _ ->

                        iterateObjects(this)
                    }

                    deployed = true
                }
            }
        }

    }

    private fun showButton(show: Boolean) {
        if (show) {
            buttonCheck.isEnabled = true
            buttonCheck.visibility = View.VISIBLE
        } else {
            buttonCheck.isEnabled = false
            buttonCheck.visibility = View.GONE
        }
    }

    private fun checkPassword() {

        if (checkCombination()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.alert_correct_title_string))
                .setMessage(resources.getString(R.string.alert_correct_message_string))
                .setPositiveButton(resources.getString(R.string.alert_correct_string)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.alert_wrong_title_string))
                .setMessage(resources.getString(R.string.alert_wrong_message_string))
                .setNegativeButton(resources.getString(R.string.alert_wrong_string)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

    }

    private fun checkCombination(): Boolean {

        Log.i("$TAG PASS", "grid position [" + CAR_ROW_CORRECT +", "+ CAR_COL_CORRECT +"]: " +
                grid[CAR_ROW_CORRECT][CAR_COL_CORRECT]!!.state.toString())
        Log.i("$TAG PASS", "grid position [" + FOX_ROW_CORRECT +", "+ FOX_COL_CORRECT +"]: " +
                grid[FOX_ROW_CORRECT][FOX_COL_CORRECT]!!.state.toString())
        Log.i("$TAG PASS", "grid position [" + SHOE_ROW_CORRECT +", "+ SHOE_COL_CORRECT +"]: " +
                grid[SHOE_ROW_CORRECT][SHOE_COL_CORRECT]!!.state.toString())
        Log.i("$TAG PASS", "Password check: " +
                (grid[CAR_ROW_CORRECT][CAR_COL_CORRECT]!!.state == TileCombination.CAR &&
                grid[FOX_ROW_CORRECT][FOX_COL_CORRECT]!!.state == TileCombination.FOX &&
                grid[SHOE_ROW_CORRECT][SHOE_COL_CORRECT]!!.state == TileCombination.SHOE).toString())

        return grid[CAR_ROW_CORRECT][CAR_COL_CORRECT]!!.state == TileCombination.CAR &&
                grid[FOX_ROW_CORRECT][FOX_COL_CORRECT]!!.state == TileCombination.FOX &&
                grid[SHOE_ROW_CORRECT][SHOE_COL_CORRECT]!!.state == TileCombination.SHOE
    }

    // Iterate between possible models
    private fun iterateObjects(node: TranslatableNode){

        when (node.state) {
            TileCombination.EMPTY -> {
                iterateNext(node, node.state)
            }

            TileCombination.CAR -> {
                carNumber --
                iterateNext(node, node.state)
            }

            TileCombination.FOX -> {
                foxNumber --
                iterateNext(node, node.state)
            }

            TileCombination.SHOE -> {
                shoeNumber --
                iterateNext(node, node.state)
            }
        }
    }

    // Iterates to next possible model
    // @args: node that iterates; initial state of the node
    // when reached again initial state revert to empty
    private fun iterateNext(node: TranslatableNode, state: TileCombination) {

        // First tries to assign the next state in the node
        if (state == TileCombination.EMPTY) {

            // If possible assigns the next state
            if (carNumber < MAX_CAR_NUM) {
                carNumber ++
                node.state = TileCombination.CAR
                node.localRotation = Quaternion.axisAngle(Vector3(0f, 1F, 0F), -45f)
                node.renderable = carRenderable

            // If not possible retries skipping one state
            } else {
                iterateNext(node, TileCombination.CAR)
            }

        } else if (state == TileCombination.CAR) {

            // If possible assigns the next state
            if (foxNumber < MAX_FOX_NUM) {
                foxNumber ++
                node.state = TileCombination.FOX
                node.localRotation = Quaternion.axisAngle(Vector3(0f, 1F, 0F), 180f)
                node.renderable = foxRenderable
            } else {
                iterateNext(node, TileCombination.FOX)
            }

        } else if (state == TileCombination.FOX) {

            // If possible assigns the next state
            if (shoeNumber < MAX_SHOE_NUM) {
                shoeNumber ++
                node.state = TileCombination.SHOE
                node.localRotation = Quaternion.axisAngle(Vector3(0f, 1F, 0F), -45f)
                node.renderable = shoeRenderable
            } else {
                iterateNext(node, TileCombination.SHOE)
            }

        } else if (state == TileCombination.SHOE) {
            node.state = TileCombination.EMPTY
            node.localRotation = Quaternion.axisAngle(Vector3(0f, 1F, 0F), 0f)
            node.renderable = tileRenderable

        } else {
            return
        }

    }

    // Initializes the models
    private fun initModels() {
        // Create a ground renderable
        ModelRenderable.builder()
                .setSource(this, Uri.parse(getString(R.string.model_tile_string)))
                .build()
                .thenAccept { tileRenderable = it }

        // Create a ground renderable
        ModelRenderable.builder()
                .setSource(this, Uri.parse(getString(R.string.model_car_string)))
                .build()
                .thenAccept { carRenderable = it }

        // Create a ground renderable
        ModelRenderable.builder()
                .setSource(this, Uri.parse(getString(R.string.model_fox_string)))
                .build()
                .thenAccept { foxRenderable = it }

        // Create a ground renderable
        ModelRenderable.builder()
                .setSource(this, Uri.parse(getString(R.string.model_shoe_string)))
                .build()
                .thenAccept { shoeRenderable = it }
    }

    // Initializes the grid
    private fun <T> Array<Array<T>>.matrixIndices(f: (Int, Int) -> Unit) {
        this.forEachIndexed { col, array ->
            array.forEachIndexed { row, _ ->
                f(col, row)
            }
        }
    }

    private fun isGridFull(): Boolean {
        return carNumber + foxNumber + shoeNumber >= MAX_OBJECT_NUM
    }


}

