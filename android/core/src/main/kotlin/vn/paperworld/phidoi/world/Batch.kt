package vn.paperworld.phidoi.world

import vn.paperworld.phidoi.engine.*

/* =========================================================
   STATIC BATCHING — phones pay per draw call: merge each landmark's
   never-moving meshes into one mesh per material (+ one ink outline per
   thickness). Objects that animate are found by probing and left alone.
   ========================================================= */
class TransformSnap(val o: Object3D) {
    private val p = o.position.clone(); private val r = Euler(o.rotation.x, o.rotation.y, o.rotation.z, o.rotation.order)
    private val q = Quat().copy(o.quaternion); private val s = o.scale.clone(); private val v = o.visible
    fun changed() = p.distanceToSquared(o.position) > 1e-10 || r.x != o.rotation.x || r.y != o.rotation.y || r.z != o.rotation.z ||
        (o.useQuat && (q.x != o.quaternion.x || q.y != o.quaternion.y || q.z != o.quaternion.z || q.w != o.quaternion.w)) || s.distanceToSquared(o.scale) > 1e-10 || v != o.visible
    fun restore() { o.position.copy(p); o.rotation.set(r.x, r.y, r.z, r.order); o.quaternion.copy(q); o.scale.copy(s); o.visible = v }
}

private class Piece(val mesh: Mesh, val rel: Mat4, val start: Int, val count: Int)
private class Acc { val pos = FloatList(); val nor = FloatList(); val uv = FloatList(); val col = FloatList(); val onor = FloatList(); val idx = IntList(); var nv = 0 }

/** candidate roots: top-level scene children that are landmark groups/meshes */
fun World.batchRoots(skip: Set<Object3D>): List<Object3D> = scene.children.filter { o ->
    o !in skip && o !in cellGroupSet && o !is InstancedMesh && o !is Points && o !is Lines &&
        !(o is Mesh && o.children.isEmpty() && o.geometry.index != null && o.geometry.color != null) // terrain tiles
}

/**
 * Merge the static meshes under each root. [dynamic] holds every object whose transform or visibility changes at
 * runtime; such objects and everything below them keep their own meshes. Returns the number of meshes merged away.
 */
fun World.batchStatics(roots: List<Object3D>, dynamic: Set<Object3D>): Int {
    var merged = 0
    val inv = Mat4(); val rel = Mat4()
    for (root in roots) {
        root.updateWorldFromParents()
        root.updateMatrixWorld()
        inv.copy(root.matrixWorld).invert()
        // gather
        val solid = LinkedHashMap<String, Pair<Material, ArrayList<Piece>>>()
        val inkB = LinkedHashMap<Double, ArrayList<Piece>>()
        val removed = ArrayList<Mesh>()
        fun visit(o: Object3D, frozen: Boolean) {
            val fz = frozen || o in dynamic || !o.visible
            if (o is Mesh && o !is InstancedMesh && !fz && o.userData !== World.OUTLINE && batchable(o)) {
                rel.multiplyMatrices(inv, o.matrixWorld)
                val mats = o.materials
                val parts = ArrayList<Pair<Material, IntArray>>()
                val total = o.geometry.index?.size ?: o.geometry.vertexCount
                if (mats != null) for (g in o.geometry.groups) parts.add(Pair(mats[g[2]], intArrayOf(g[0], minOf(g[1], total - g[0])))) else parts.add(Pair(o.material, intArrayOf(0, total)))
                for ((m, r) in parts) {
                    val key = "${m.id}|${o.castShadow}|${o.receiveShadow}"
                    solid.getOrPut(key) { Pair(m, ArrayList()) }.second.add(Piece(o, rel.clone(), r[0], r[1]))
                }
                // its ink outline child (if visible and static)
                for (ch in o.children) if (ch is Mesh && ch.userData === World.OUTLINE && ch.visible && ch !in dynamic && ch.geometry === o.geometry) {
                    val th = (ch.material as OutlineMaterial).thick
                    inkB.getOrPut(th) { ArrayList() }.add(Piece(o, rel.clone(), 0, total))
                }
                removed.add(o)
                for (ch in o.children) if (ch.userData !== World.OUTLINE) visit(ch, fz)
                return
            }
            for (ch in ArrayList(o.children)) visit(ch, fz)
        }
        visit(root, false)
        if (removed.size < 2) continue
        // build merged meshes, attached to the root so culling and statics still work per landmark
        val target: Object3D = if (root is Mesh && root in removed) Group().also { g ->
            // the root itself is merged: keep its transform on a new group
            g.position.copy(root.position); g.rotation.set(root.rotation.x, root.rotation.y, root.rotation.z, root.rotation.order); g.quaternion.copy(root.quaternion); g.useQuat = root.useQuat; g.scale.copy(root.scale)
            g.castShadow = root.castShadow; g.receiveShadow = root.receiveShadow
            val idx = scene.children.indexOf(root); scene.remove(root); scene.add(g); scene.children.remove(g); scene.children.add(idx, g)
        } else root
        for (m in removed) {
            val p = m.parent ?: continue
            if (m === root) continue
            // children that were not merged (dynamic / instanced) move up to the mesh's parent with their world transform kept
            for (ch in ArrayList(m.children)) if (ch.userData !== World.OUTLINE && ch !in removed) reparentKeep(ch, p)
            p.remove(m)
        }
        if (root is Mesh && root in removed) for (ch in ArrayList(root.children)) if (ch.userData !== World.OUTLINE && ch !in removed) target.add(ch)
        for ((_, pr) in solid.values.withIndex()) {
            val (mat, pieces) = pr
            val geo = mergePieces(pieces, mat, false) ?: continue
            val mesh = Mesh(geo, mat)
            mesh.castShadow = pieces[0].mesh.castShadow; mesh.receiveShadow = pieces[0].mesh.receiveShadow
            mesh.renderOrder = pieces[0].mesh.renderOrder
            target.add(mesh)
        }
        for ((th, pieces) in inkB) {
            val geo = mergePieces(pieces, null, true) ?: continue
            val o = Mesh(geo, outlineMat(th)); o.userData = World.OUTLINE
            target.add(o)
        }
        merged += removed.size
    }
    return merged
}

private fun batchable(o: Mesh): Boolean {
    val g = o.geometry
    if (g.dynamic || g.colorSize != 3 || g.drawCount >= 0) return false
    val ms = o.materials ?: listOf(o.material)
    for (m in ms) if (m.transparent || m !is ToonMaterial) return false
    if (o.materials != null && g.groups.isEmpty()) return false
    return true
}

private fun reparentKeep(o: Object3D, newParent: Object3D) {
    o.updateWorldFromParents()
    newParent.updateWorldFromParents()
    val m = Mat4().multiplyMatrices(Mat4().copy(newParent.matrixWorld).invert(), o.matrixWorld)
    val p = Vec3(); val q = Quat(); val s = Vec3()
    m.decompose(p, q, s)
    o.position.copy(p); o.quaternion.copy(q); o.useQuat = true; o.scale.copy(s)
    o.parent?.remove(o)
    newParent.add(o)
}

private fun mergePieces(pieces: List<Piece>, mat: Material?, ink: Boolean): Geometry? {
    val tm = mat as? ToonMaterial
    val wantUv = tm?.map != null
    val wantCol = tm?.vertexColors == true
    val A = Acc()
    val nm = Mat3(); val v = Vec3()
    val m3 = Mat3()
    for (pc in pieces) {
        val g = pc.mesh.geometry
        if (ink && g.onormal == null) g.addONormal()
        val nor = g.normal ?: g.computeVertexNormals().normal!!
        nm.getNormalMatrix(pc.rel)
        if (ink) setMat3(m3, pc.rel)
        val idx = g.index
        // which source vertices this piece uses
        val verts: IntArray; val tris: IntArray
        if (idx != null) {
            val used = HashMap<Int, Int>(); val vl = IntList(); val tl = IntList()
            for (k in pc.start until pc.start + pc.count) { val s = idx[k]; val d = used.getOrPut(s) { vl.add(s); vl.n - 1 }; tl.add(d) }
            verts = vl.toArray(); tris = tl.toArray()
        } else {
            verts = IntArray(pc.count) { pc.start + it }; tris = IntArray(pc.count) { it }
        }
        val base = A.nv
        for (s in verts) {
            v.set(g.position[s * 3].toDouble(), g.position[s * 3 + 1].toDouble(), g.position[s * 3 + 2].toDouble()).applyMatrix4(pc.rel)
            A.pos.add3(v.x, v.y, v.z)
            if (ink) {
                val on = g.onormal!!
                v.set(on[s * 3].toDouble(), on[s * 3 + 1].toDouble(), on[s * 3 + 2].toDouble()).applyMatrix3(m3).normalize()
                A.onor.add3(v.x, v.y, v.z)
            } else {
                v.set(nor[s * 3].toDouble(), nor[s * 3 + 1].toDouble(), nor[s * 3 + 2].toDouble()).applyMatrix3(nm).normalize()
                A.nor.add3(v.x, v.y, v.z)
                if (wantUv) { val uv = g.uv; if (uv != null) { A.uv.add(uv[s * 2]); A.uv.add(uv[s * 2 + 1]) } else { A.uv.add(0f); A.uv.add(0f) } }
                if (wantCol) { val c = g.color; if (c != null) { A.col.add(c[s * 3]); A.col.add(c[s * 3 + 1]); A.col.add(c[s * 3 + 2]) } else A.col.add3(1.0, 1.0, 1.0) }
            }
        }
        // a mirrored transform flips the winding
        val flip = pc.rel.determinant() < 0
        var k = 0
        while (k + 2 < tris.size) {
            if (flip) { A.idx.add3(base + tris[k], base + tris[k + 2], base + tris[k + 1]) } else A.idx.add3(base + tris[k], base + tris[k + 1], base + tris[k + 2])
            k += 3
        }
        A.nv += verts.size
    }
    if (A.nv == 0) return null
    val out = Geometry.of(A.pos.toArray(), if (ink) null else A.nor.toArray(), if (wantUv && !ink) A.uv.toArray() else null, A.idx.toArray())
    if (ink) { out.onormal = A.onor.toArray(); out.normal = out.onormal } else if (wantCol) out.color = A.col.toArray()
    out.computeBoundingSphere()
    return out
}
private fun setMat3(m3: Mat3, m: Mat4) {
    val e = m.e
    m3.e[0] = e[0]; m3.e[1] = e[1]; m3.e[2] = e[2]; m3.e[3] = e[4]; m3.e[4] = e[5]; m3.e[5] = e[6]; m3.e[6] = e[8]; m3.e[7] = e[9]; m3.e[8] = e[10]
}
