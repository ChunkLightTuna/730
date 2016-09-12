package edu.unh.cs.ai.a1

import java.util.*
import kotlin.system.exitProcess

/**
 * Vacuum world solver
 *
 * **tiles**
 *    @  robot
 *
 *    #  blocked
 *
 *    *  dirt
 *
 *    _  empty
 *
 * **actions**
 * N, S, E, W, or V
 *
 * used the following for reference
 * wikipedia.org/wiki/A*_search_algorithm
 * wikipedia.org/wiki/Admissible_heuristic
 */
fun main(args: Array<String>) {

    val algorithms: Map<String, (World, Node) -> Result> = mapOf(
            "uniform-cost" to ::proto,
            "depth-first" to ::dfs,
            "depth-first-id" to ::depth_first_id,
            "a-star" to ::proto,
            "ida-star" to ::ida_star,
            "greedy" to ::proto)

    val heuristics: Map<String, (Node) -> Int> = mapOf(
            "h0" to { node -> 0 },
            "h1" to { node -> node.state.dirt.size },
            "h2" to { node -> node.closestDirt() },
            "h3" to { node -> node.state.charge }
    )

    var algorithm = { world: World, node: Node -> Result(null, 0, 0) }
    var hFun: (Node) -> Int = { node -> 0 }
    var gFun: (Node) -> Int = { node -> node.parent!!.gValue + 1 }

    var hardMode = false
    var algSet = false
    var hSet = false

    args.forEach {
        if (!algSet && algorithms.containsKey(it)) {
            algorithm = algorithms[it]!!
            if (it == "uniform-cost" || it == "depth-first" || it == "depth-first-id") {
                hFun = { node -> 0 } //enforce no heuristic for a1 methods
                hSet = true
            } else if (it == "greedy") {
                gFun = { node -> 0 }
            }
            algSet = true
        } else if (!hSet && heuristics.containsKey(it)) {
            hFun = heuristics[it]!!
            hSet = true
        } else if (it == "-battery") {
            hardMode = true
        }
    }


    if (!algSet) {
        println(algorithms.keys.joinToString(prefix = "Please specify a search method. (", separator = ", ", postfix = ")"))
        exitProcess(1)
    }

    //not the fastest, but maybe doesn't matter
    val startDirt = mutableListOf<Coord>()
    val blocked = mutableListOf<Coord>()
    val batteries = mutableListOf<Coord>()

    val cols = readLine().toInt()
    val rows = readLine().toInt()

    var startPos = Coord(-1, -1)

    val grid = Array(rows, { CharArray(cols) }) //had used this for initial debugging. Not really used now, but too lazy to remove

    for (y in grid.indices) {
        val next = readLine()
        for (x in grid[y].indices) {
            grid[y][x] = next[x]
            val coord = Coord(x, y)
            when (grid[y][x]) {
                '@' -> startPos = coord
                '#' -> blocked.add(coord)
                '*' -> startDirt.add(coord)
                ':' -> batteries.add(coord)
            }
        }
    }

    val world = World(cols, rows, blocked, batteries, hardMode, gFun, hFun)

    val startNode = Node(State(startPos, world.fullCharge, startDirt))
    startNode.fValue = hFun(startNode) //manually set heuristic for startNode. remember gValue = 0

    val result = algorithm(world, startNode)

    var node: Node? = result.node
    if (node == null) {
        println("no solution found")
    }

    val stack = Stack<Char>()
    while (node != null && node.route != null) {
        stack.add(node.route)
        node = node.parent
    }
    while (!stack.isEmpty()) {
        println(stack.pop())
    }

    println(result.generated.toString() + " nodes generated")
    println(result.expanded.toString() + " nodes expanded")
}

data class Result(val node: Node?, val generated: Int, val expanded: Int)

fun ida_star(world: World, startNode: Node): Result {

    //todo
    return proto(world, startNode)
}

fun proto(world: World, startNode: Node): Result {
    val openList = PriorityQueue<Node>()
    openList.add(startNode)

    var gen = 1
    var exp = 0

    val closedList = HashSet<Node>()

    var goal: Node? = null

    while (!openList.isEmpty()) {
        val current = openList.remove()

        if (current.state.dirt.isEmpty()) {
            goal = current
            break
        }

        closedList.add(current)
        exp++
        val neighbors = current.genNeighbors(world)
        gen += neighbors.size

        neighbors.forEach {
            if (!closedList.contains(it)) {
                openList.add(it)
            }
        }
    }
    return Result(goal, gen, exp)
}

/**
 * Depth First
 */
fun dfs(world: World, startNode: Node): Result {
    return dfs_base(world, startNode, Int.MAX_VALUE)
}

fun depth_first_id(world: World, startNode: Node): Result {

    var maxDepth = 0

    while (true) {
        maxDepth++
        val result = dfs_base(world, startNode, maxDepth++)
        if (result.node != null)
            return result
    }
}

fun dfs_base(world: World, startNode: Node, maxDepth: Int): Result {
    val openList = Stack<Node>()
    openList.add(startNode)

    var gen = 1
    var exp = 0

    var goal: Node? = null

    while (!openList.isEmpty()) {
        val node = openList.pop()

        if (node.state.dirt.isEmpty()) {
            goal = node
            break
        }

        if (loopCheck(node) && node.gValue <= maxDepth) {
            exp++
            val genNodes = node.genNeighbors(world)
            gen += genNodes.size
            openList.addAll(genNodes)
        }
    }

    return Result(goal, gen, exp)
}

fun loopCheck(node: Node): Boolean {
    var ancestor: Node? = node.parent
    while (ancestor != null && node != ancestor) {
        ancestor = ancestor.parent
    }
    return ancestor == null
}

fun readLine(): String {
    val line = kotlin.io.readLine()

    if (line.isNullOrBlank()) {
        println("Input formatted improperly.")
        exitProcess(1)
    }

    return line!!
}

/**
 * @param pos - agent coords
 * @param charge - remaining charge
 * @param dirt - list of dirt coords
 */
data class State(val pos: Coord, val charge: Int, val dirt: List<Coord>) {
}

/**
 * to search we build out a tree of nodes. Nodes contain state as well as how it got there
 *
 * Primary constructor only used for root node while secondary is used for all subsequent nodes
 */
data class Node(
        val state: State,
        val route: Char? = null,
        var parent: Node? = null,
        var gValue: Int = 0,
        var fValue: Int = 0) : Comparable<Node> {

    //gValue is the cost of going from startNode to this node (0 for startNode)

    //fValue is the cost of getting from startNode to goalNode by traveling through this node
    //startNode -> this the gValue
    //this -> goalNode is estimated by hValue
    //so fValue = gValue + hValue
    //or hValue = fValue - gValue

    constructor(
            state: State,
            route: Char,
            parent: Node,
            gFun: (Node) -> Int,
            hFun: (Node) -> Int) : this(state = state, route = route, parent = parent) {
        gValue = gFun(this)
        fValue = gValue + hFun(this)
    }

    override fun compareTo(other: Node): Int {
        return this.fValue.compareTo(other.fValue)
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 11 * hash + state.pos.x
        hash = 13 * hash + state.pos.y

        var dirtX = 0
        var dirtY = 0

        for ((x, y) in state.dirt) {
            dirtX += x
            dirtY += y
        }

        hash = 17 * hash + dirtX
        hash = 19 * hash + dirtY

        return hash
    }

    override fun equals(other: Any?): Boolean {
        val otherNode = other as? Node ?: return false

        return (state.pos == otherNode.state.pos &&
                state.charge == otherNode.state.charge &&
                state.dirt.size == otherNode.state.dirt.size &&
                state.dirt.containsAll(otherNode.state.dirt) &&
                otherNode.state.dirt.containsAll(state.dirt))
    }

    fun closestDirt(): Int {
        var closest = Int.MAX_VALUE

        state.dirt.forEach {
            closest = Math.min(closest, state.pos.manhattanDistance(it))
        }

        return closest
    }

    fun genNeighbors(world: World): List<Node> {

        val curPos = state.pos
        val newCharge = if (world.hardMode) state.charge - 1 else world.fullCharge

        //Vacuum >> all
        if (state.dirt.contains(curPos) && (!world.hardMode || state.charge > 0)) {
            val newState = state.copy(charge = newCharge, dirt = state.dirt.minus(curPos))

            return listOf(Node(newState, 'V', this, world.gFun, world.hFun))
        }

        val nodes = mutableListOf<Node>()

        //Recharge
        if (world.hardMode) {
            if (world.batteries.contains(curPos) &&
                    state.charge != world.fullCharge) {
                val newState = state.copy(charge = world.fullCharge)
                nodes.add(Node(newState, 'R', this, world.gFun, world.hFun))

            } else if (state.charge == 0) {
                //dead batteries!
                return emptyList()
            }
        }
        //Move North
        if (curPos.y != 0) {
            val dest = curPos.copy(y = curPos.y - 1)
            if (!world.blocked.contains(dest)) {
                val newState = state.copy(charge = newCharge, pos = dest)
                nodes.add(Node(newState, 'N', this, world.gFun, world.hFun))
            }
        }
        //Move East
        if (curPos.x != world.cols - 1) {
            val dest = curPos.copy(x = curPos.x + 1)
            if (!world.blocked.contains(dest)) {
                val newState = state.copy(charge = newCharge, pos = dest)
                nodes.add(Node(newState, 'E', this, world.gFun, world.hFun))
            }
        }
        //Move South
        if (curPos.y != world.rows - 1) {
            val dest = curPos.copy(y = curPos.y + 1)
            if (!world.blocked.contains(dest)) {
                val newState = state.copy(charge = newCharge, pos = dest)
                nodes.add(Node(newState, 'S', this, world.gFun, world.hFun))
            }
        }
        //Move West
        if (curPos.x != 0) {
            val dest = curPos.copy(x = curPos.x - 1)
            if (!world.blocked.contains(dest)) {
                val newState = state.copy(charge = newCharge, pos = dest)
                nodes.add(Node(newState, 'W', this, world.gFun, world.hFun))
            }
        }
        return nodes
    }

    /*default g cost, no heuristic!
    fun genNeighbors(world: World): List<Node> {
        return genNeighbors(world, { node -> node.parent!!.gValue + 1 }, { node -> 0 })
    }*/
}

/**
 * Stuff that doesn't change
 */
data class World(
        val cols: Int,
        val rows: Int,
        val blocked: List<Coord>,
        val batteries: List<Coord>,
        val hardMode: Boolean,
        val gFun: (Node) -> Int,
        val hFun: (Node) -> Int,
        val fullCharge: Int = 2 * (cols + rows - 2) + 1)

/**
 * lazy pair/point
 */
data class Coord(val x: Int, val y: Int) {

    fun manhattanDistance(other: Coord) = Math.abs(x - other.x) + Math.abs(y - other.y)
}